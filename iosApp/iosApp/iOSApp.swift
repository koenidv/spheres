import SwiftUI
import StoreKit
import ComposeApp

@main
struct iOSApp: App {
    init() {
        IosPostHogInitializer.shared.setup()
        IosStoreKit2Bridge.shared.registerEntitlementChecker { productId in
            Task {
                await Self.refreshEntitlement(productId: productId)
            }
        }
        Task.detached(priority: .background) {
            for await result in Transaction.updates {
                guard case .verified(let tx) = result else { continue }
                if tx.productID == LogicKt.UNLIMITED_PRODUCT_ID {
                    let active = (tx.revocationDate == nil)
                    IosStoreKit2Bridge.shared.reportEntitlement(
                        entitled: active,
                        transactionId: active ? String(tx.originalID) : nil
                    )
                }
                await tx.finish()
            }
        }
    }

    private static func refreshEntitlement(productId: String) async {
        var activeTxId: String? = nil
        for await result in Transaction.currentEntitlements {
            if case .verified(let tx) = result,
               tx.productID == productId,
               tx.revocationDate == nil {
                activeTxId = String(tx.originalID)
                break
            }
        }
        IosStoreKit2Bridge.shared.reportEntitlement(
            entitled: activeTxId != nil,
            transactionId: activeTxId
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .commands {
            CommandGroup(after: .newItem) {
                Button("New Connection") { AppShortcuts.shared.fire(id: "new") }
                    .keyboardShortcut("n")
                Button("Log Interaction") { AppShortcuts.shared.fire(id: "log") }
                    .keyboardShortcut("l")
            }
            CommandMenu("Go") {
                Button("Radar") { AppShortcuts.shared.fire(id: "radar") }
                    .keyboardShortcut("1")
                Button("Due") { AppShortcuts.shared.fire(id: "due") }
                    .keyboardShortcut("2")
                Button("Spheres") { AppShortcuts.shared.fire(id: "spheres") }
                    .keyboardShortcut("3")
                Button("Map") { AppShortcuts.shared.fire(id: "map") }
                    .keyboardShortcut("4")
                Divider()
                Button("Search Connections") { AppShortcuts.shared.fire(id: "search") }
                    .keyboardShortcut("f")
                Button("Settings") { AppShortcuts.shared.fire(id: "settings") }
                    .keyboardShortcut(",")
            }
        }
    }
}