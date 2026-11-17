import SwiftUI
import StoreKit
import ComposeApp

@main
struct iOSApp: App {
    init() {
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
    }
}