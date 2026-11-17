const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_trader_1";
const BOB_UID = "bob_trader_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validAccountPayload(uid) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    displayName: "Alice Quant",
    accountBalance: 25000.0,
    startingBalance: 25000.0,
    dailyStartEquity: 25000.0,
    dailyRealizedPnl: 0.0,
    realizedPnl: 0.0,
    totalTrades: 0,
    winningTrades: 0,
    botArmed: false,
    circuitBreakerTripped: false,
    executionMode: "PAPER",
    createdAt: now,
    updatedAt: now,
  };
}

function validConfigPayload(uid) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    riskPerTradePercent: 1.0,
    maxDailyDrawdownPercent: 3.0,
    maxOpenPositions: 3,
    minRiskRewardRatio: 2.0,
    atrStopMultiplier: 1.5,
    trailingStopEnabled: true,
    breakEvenTriggerR: 1.0,
    emaFastPeriod: 9,
    emaSlowPeriod: 21,
    rsiPeriod: 14,
    rsiOversold: 30.0,
    rsiOverbought: 70.0,
    minConfluenceScore: 3,
    watchedPairs: ["EUR/USD", "GBP/USD", "USD/JPY"],
    createdAt: now,
    updatedAt: now,
  };
}

function validOpenTradePayload(uid) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    pair: "EUR/USD",
    side: "BUY",
    status: "OPEN",
    lotSize: 0.5,
    units: 50000,
    entryPrice: 1.0842,
    stopLoss: 1.0812,
    takeProfit: 1.0902,
    trailingStopPrice: 0.0,
    exitPrice: 0.0,
    riskAmountUsd: 150.0,
    riskRewardRatio: 2.0,
    realizedPnlUsd: 0.0,
    realizedPips: 0.0,
    exitReason: "ACTIVE",
    signalSummary: "EMA9>21 • MACD Bullish • RSI 54.0",
    confluenceScore: 3,
    timeframe: "M15",
    openedAtMillis: Date.now() - 2000,
    closedAtMillis: 0,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users cannot read or write any trading data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set(validAccountPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("trades").get()
  );
});

test("Cross-user isolation prevents Bob from reading or modifying Alice account, config, or trades", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validAccountPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("configs").doc("default").set(validConfigPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("trades").doc("trade_1").set(validOpenTradePayload(ALICE_UID))
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ botArmed: true, updatedAt: new Date(Date.now() - 1000) })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("trades").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("trades").doc("trade_2").set(validOpenTradePayload(ALICE_UID))
  );
});

test("TradingAccount supports action-based updates and rejects ghost fields or future timestamps", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const accountRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(accountRef.set(validAccountPayload(ALICE_UID)));

  // Arm bot
  await assertSucceeds(
    accountRef.update({ botArmed: true, updatedAt: new Date(Date.now() - 1000) })
  );

  // Reject ghost field shadow update
  await assertFails(
    accountRef.update({ botArmed: false, adminOverride: true, updatedAt: new Date(Date.now() - 500) })
  );

  // Reject future timestamp
  await assertFails(
    accountRef.update({ botArmed: false, updatedAt: new Date(Date.now() + 60000) })
  );

  // Reject immutable createdAt modification
  await assertFails(
    accountRef.update({ createdAt: new Date(Date.now() - 500), updatedAt: new Date(Date.now() - 500) })
  );
});

test("BotRiskConfig validates strict bounds (EMA fast < slow, risk <= 5%)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const configRef = aliceDb.collection("users").doc(ALICE_UID).collection("configs").doc("default");

  await assertSucceeds(configRef.set(validConfigPayload(ALICE_UID)));

  // Valid update
  await assertSucceeds(
    configRef.update({
      riskPerTradePercent: 1.5,
      maxDailyDrawdownPercent: 4.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject excessive risk (> 5.0%)
  await assertFails(
    configRef.update({
      riskPerTradePercent: 20.0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject invalid EMA inversion (fast >= slow)
  await assertFails(
    configRef.update({
      emaFastPeriod: 25,
      emaSlowPeriod: 20,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("ForexTrade enforces @DocumentId exclusion, trailing stop update, and terminal CLOSED lock", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tradesCol = aliceDb.collection("users").doc(ALICE_UID).collection("trades");

  // Reject @DocumentId "id" key inside payload
  await assertFails(
    tradesCol.doc("trade_bad").set({
      id: "trade_bad",
      ...validOpenTradePayload(ALICE_UID),
    })
  );

  // Valid open trade creation
  const tradeRef = tradesCol.doc("trade_1");
  await assertSucceeds(tradeRef.set(validOpenTradePayload(ALICE_UID)));

  // Valid list query on user subcollection
  await assertSucceeds(tradesCol.get());

  // Valid trailing stop ratchet while OPEN
  await assertSucceeds(
    tradeRef.update({
      trailingStopPrice: 1.0842,
      stopLoss: 1.0842,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Valid close transition
  await assertSucceeds(
    tradeRef.update({
      status: "CLOSED",
      exitPrice: 1.0902,
      realizedPnlUsd: 300.0,
      realizedPips: 60.0,
      exitReason: "TP_HIT",
      closedAtMillis: Date.now(),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Valid sorted Trade History query (matches TradingRepository.observeExecutedTradeHistory)
  const historySnap = await assertSucceeds(
    tradesCol.where("closedAtMillis", ">", 0).orderBy("closedAtMillis", "desc").get()
  );
  if (historySnap.size !== 1) {
    throw new Error(`Expected 1 closed trade in history query, got ${historySnap.size}`);
  }

  // Terminal state lock: subsequent updates on CLOSED trade MUST fail
  await assertFails(
    tradeRef.update({
      realizedPnlUsd: 9999.0,
      updatedAt: new Date(Date.now() - 200),
    })
  );
  await assertFails(
    tradeRef.update({
      status: "OPEN",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});
