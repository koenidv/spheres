const crypto = require("crypto");
const { onRequest } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const logger = require("firebase-functions/logger");
const admin = require("firebase-admin");
const { GoogleAuth } = require("google-auth-library");

admin.initializeApp();
const db = admin.firestore();

const ANDROID_PACKAGE_NAME = "dev.koeni.spheres";
const PLAY_SCOPE = "https://www.googleapis.com/auth/androidpublisher";

const googleAuth = new GoogleAuth({ scopes: [PLAY_SCOPE] });

async function revokeByPurchaseToken(purchaseToken, reason) {
  if (!purchaseToken) return 0;
  const snap = await db
    .collectionGroup("billing")
    .where("purchaseToken", "==", purchaseToken)
    .get();

  if (snap.empty) return 0;
  const now = Date.now();
  const batch = db.batch();
  let updated = 0;
  for (const doc of snap.docs) {
    if (doc.data()?.unlocked !== true) continue;
    updated += 1;
    batch.set(
      doc.ref,
      {
        unlocked: false,
        revokedAt: now,
        revocationReason: reason,
      },
      { merge: true }
    );
  }
  if (updated > 0) {
    await batch.commit();
  }
  return updated;
}

async function revokeUserEntitlement(uid, reason) {
  if (!uid) return false;
  const ref = db.doc(`users/${uid}/billing/unlimited`);
  const snap = await ref.get();
  if (!snap.exists || snap.data()?.unlocked !== true) return false;
  await ref.set(
    {
      unlocked: false,
      revokedAt: Date.now(),
      revocationReason: reason,
    },
    { merge: true }
  );
  return true;
}

function decodeJwsPayload(jws) {
  if (typeof jws !== "string") return null;
  const parts = jws.split(".");
  if (parts.length !== 3) return null;
  try {
    const json = Buffer.from(parts[1], "base64url").toString("utf8");
    return JSON.parse(json);
  } catch {
    return null;
  }
}

async function fetchPlayVoidedPurchases(pageToken = null) {
  const client = await googleAuth.getClient();
  const tokenRes = await client.getAccessToken();
  const url = new URL(
    `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${ANDROID_PACKAGE_NAME}/purchases/voidedpurchases`
  );
  if (pageToken) url.searchParams.set("token", pageToken);

  const res = await fetch(url.toString(), {
    headers: { Authorization: `Bearer ${tokenRes.token}` },
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(`Play voidedpurchases API error ${res.status}: ${body}`);
  }
  return res.json();
}

/**
 * Periodically polls Google Play's Voided Purchases API to revoke
 * refunded or charged-back one-time purchases in Firestore.
 */
exports.syncPlayVoidedPurchases = onSchedule("every 60 minutes", async () => {
  let pageToken = null;
  let revokedCount = 0;

  do {
    const data = await fetchPlayVoidedPurchases(pageToken);
    const voided = data.voidedPurchases || [];
    for (const item of voided) {
      revokedCount += await revokeByPurchaseToken(item.purchaseToken, "play_voided_purchase");
    }
    pageToken = data.tokenPagination?.nextPageToken || null;
  } while (pageToken);

  logger.info("syncPlayVoidedPurchases complete", { revokedCount });
});

/**
 * Google Play Real-Time Developer Notifications (RTDN) Pub/Sub push webhook.
 * Handles ONE_TIME_PRODUCT_CANCELED (notificationType === 2) and voidedPurchaseNotification.
 */
exports.playBillingWebhook = onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  const encoded = req.body?.message?.data;
  if (!encoded) {
    res.status(400).send("Missing Pub/Sub message data");
    return;
  }

  let notification;
  try {
    notification = JSON.parse(Buffer.from(encoded, "base64").toString("utf8"));
  } catch {
    res.status(400).send("Invalid base64 JSON");
    return;
  }

  const oneTime = notification.oneTimeProductNotification;
  if (oneTime && oneTime.notificationType === 2) {
    const count = await revokeByPurchaseToken(oneTime.purchaseToken, "play_rtdn_canceled");
    logger.info("Revoked Play one-time product via RTDN", { count, sku: oneTime.sku });
  }

  const voided = notification.voidedPurchaseNotification;
  if (voided?.purchaseToken) {
    const count = await revokeByPurchaseToken(voided.purchaseToken, "play_rtdn_voided");
    logger.info("Revoked Play voided purchase via RTDN", { count });
  }

  res.status(200).send("OK");
});

/**
 * Apple App Store Server Notifications v2 webhook.
 * Handles REFUND and REVOKE events by matching originalTransactionId or appAccountToken.
 */
exports.appStoreWebhook = onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  const payload = decodeJwsPayload(req.body?.signedPayload);
  if (!payload) {
    res.status(400).send("Invalid signedPayload");
    return;
  }

  const type = payload.notificationType;
  if (type === "REFUND" || type === "REVOKE") {
    const tx = decodeJwsPayload(payload.data?.signedTransactionInfo);
    const originalTxId = tx?.originalTransactionId ? String(tx.originalTransactionId) : null;
    const appAccountToken = tx?.appAccountToken || null;

    let revoked = 0;
    if (originalTxId) {
      revoked += await revokeByPurchaseToken(originalTxId, `app_store_${type.toLowerCase()}`);
    }
    if (revoked === 0 && appAccountToken) {
      const didRevoke = await revokeUserEntitlement(appAccountToken, `app_store_${type.toLowerCase()}`);
      if (didRevoke) revoked += 1;
    }
    logger.info("Processed App Store revocation", { type, revoked });
  }

  res.status(200).send("OK");
});

/**
 * Verifies the Bearer Firebase ID token on the request. Responds with 401 and returns null when invalid.
 */
async function authenticate(req, res) {
  const authHeader = req.headers.authorization || "";
  const idToken = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : null;
  if (!idToken) {
    res.status(401).json({ error: "Missing Bearer token" });
    return null;
  }
  try {
    const decoded = await admin.auth().verifyIdToken(idToken);
    return decoded.uid;
  } catch {
    res.status(401).json({ error: "Invalid Firebase ID token" });
    return null;
  }
}

/**
 * On-demand purchase verification & 1-to-1 account claim / transfer endpoint.
 * Verifies the caller's Firebase ID token, checks Play Developer API when available,
 * and enforces that a single purchaseToken / transactionId belongs to at most one uid.
 */
exports.verifyPurchase = onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  const uid = await authenticate(req, res);
  if (!uid) return;

  const { platform, productId, purchaseToken, allowTransfer = false } = req.body || {};
  if (!platform || !productId || !purchaseToken || (platform !== "android" && platform !== "ios")) {
    res.status(400).json({ error: "platform ('android'|'ios'), productId, and purchaseToken are required" });
    return;
  }

  if (platform === "android") {
    try {
      const client = await googleAuth.getClient();
      const tokenRes = await client.getAccessToken();
      const url = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${ANDROID_PACKAGE_NAME}/purchases/products/${encodeURIComponent(productId)}/tokens/${encodeURIComponent(purchaseToken)}`;
      const playRes = await fetch(url, {
        headers: { Authorization: `Bearer ${tokenRes.token}` },
      });

      if (playRes.ok) {
        const purchase = await playRes.json();
        // purchaseState: 0 = Purchased, 1 = Canceled, 2 = Pending
        if (purchase.purchaseState !== 0) {
          await db.doc(`users/${uid}/billing/unlimited`).set(
            {
              unlocked: false,
              productId,
              platform: "android",
              revokedAt: Date.now(),
              revocationReason: "play_verify_not_purchased",
            },
            { merge: true }
          );
          res.status(403).json({ status: "not_purchased", unlocked: false, purchaseState: purchase.purchaseState });
          return;
        }
      } else if (playRes.status === 400 || playRes.status === 404 || playRes.status === 410) {
        const detail = await playRes.text();
        res.status(403).json({ status: "invalid_purchase", unlocked: false, detail });
        return;
      } else {
        // E.g. 401/403 when Play Console service-account permissions aren't linked yet
        logger.warn("Play API verification skipped due to status", { status: playRes.status });
      }
    } catch (err) {
      logger.warn("Play API verification error, continuing with claim check", { message: err?.message });
    }
  }

  const claimId = crypto
    .createHash("sha256")
    .update(`${platform}:${String(purchaseToken)}`)
    .digest("hex");
  const claimRef = db.doc(`billing_claims/${claimId}`);
  const userBillingRef = db.doc(`users/${uid}/billing/unlimited`);
  const now = Date.now();

  const outcome = await db.runTransaction(async (tx) => {
    const claimSnap = await tx.get(claimRef);
    const existing = claimSnap.exists ? claimSnap.data() : null;

    if (existing && existing.ownerUid && existing.ownerUid !== uid) {
      if (!allowTransfer) {
        return { status: "owned_by_other", unlocked: false };
      }
      const prevBillingRef = db.doc(`users/${existing.ownerUid}/billing/unlimited`);
      tx.set(
        prevBillingRef,
        {
          unlocked: false,
          revokedAt: now,
          revocationReason: "transferred_to_other_account",
        },
        { merge: true }
      );
      tx.set(
        claimRef,
        {
          ownerUid: uid,
          platform,
          productId,
          purchaseToken: String(purchaseToken),
          claimedAt: now,
          transferredFrom: existing.ownerUid,
        },
        { merge: true }
      );
      tx.set(
        userBillingRef,
        {
          unlocked: true,
          unlockedAt: now,
          productId,
          platform,
          purchaseToken: String(purchaseToken),
          revokedAt: null,
        },
        { merge: true }
      );
      return { status: "transferred", unlocked: true };
    }

    tx.set(
      claimRef,
      {
        ownerUid: uid,
        platform,
        productId,
        purchaseToken: String(purchaseToken),
        claimedAt: now,
      },
      { merge: true }
    );
    tx.set(
      userBillingRef,
      {
        unlocked: true,
        unlockedAt: now,
        productId,
        platform,
        purchaseToken: String(purchaseToken),
        revokedAt: null,
      },
      { merge: true }
    );
    return { status: "claimed", unlocked: true };
  });

  if (outcome.status === "owned_by_other") {
    res.status(409).json(outcome);
    return;
  }
  res.status(200).json(outcome);
});

/**
 * Permanently deletes the caller's account: all Firestore data, uploaded photos,
 * purchase claims (so the purchase can be restored on a new account) and the Auth user.
 */
exports.deleteAccount = onRequest(async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method Not Allowed");
    return;
  }

  const uid = await authenticate(req, res);
  if (!uid) return;

  try {
    await db.recursiveDelete(db.doc(`users/${uid}`));

    const claims = await db.collection("billing_claims").where("ownerUid", "==", uid).get();
    if (!claims.empty) {
      const batch = db.batch();
      claims.docs.forEach((doc) => batch.delete(doc.ref));
      await batch.commit();
    }

    await admin.storage().bucket().deleteFiles({ prefix: `users/${uid}/` });
    await admin.auth().deleteUser(uid);
  } catch (err) {
    logger.error("Account deletion failed", { uid, message: err?.message });
    res.status(500).json({ error: "Account deletion failed" });
    return;
  }

  logger.info("Deleted account", { uid });
  res.status(200).json({ status: "deleted" });
});
