const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const LIST_CODE = "455344";

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

test("Unauthenticated user cannot read or write users, lists, or items", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("lists").doc(LIST_CODE).get());
  await assertFails(unauthDb.collection("lists").doc(LIST_CODE).collection("items").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      name: "Alice",
      listCode: LIST_CODE,
      role: "opiekun",
      createdAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profile: owner can create, read, and update; other users are blocked", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      name: "Kasia",
      listCode: LIST_CODE,
      role: "opiekun",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      name: "Hacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      name: "Kasia Nowak",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Shared list and items: paired users can create, observe, update status, and delete; invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("lists").doc(LIST_CODE).set({
      code: LIST_CODE,
      name: "Wspólna Lista 455344",
      ownerUid: ALICE_UID,
      createdAt: now,
    })
  );

  // Listing all lists is forbidden
  await assertFails(bobDb.collection("lists").get());

  // Bob can check list exists by 6-digit code
  await assertSucceeds(bobDb.collection("lists").doc(LIST_CODE).get());

  // Alice adds an item (mirrors ChecklistRepository.addItem)
  await assertSucceeds(
    aliceDb.collection("lists").doc(LIST_CODE).collection("items").doc("item_1").set({
      listCode: LIST_CODE,
      text: "Wziąć leki o 12:00",
      senderUid: ALICE_UID,
      senderName: "Kasia",
      deliveryMode: 2,
      scheduledTime: Date.now() + 10000,
      status: "ringing",
      reminderAcknowledgedTime: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject payload containing ghost 'id' field or spoofed senderUid
  await assertFails(
    aliceDb.collection("lists").doc(LIST_CODE).collection("items").doc("item_bad").set({
      id: "item_bad",
      listCode: LIST_CODE,
      text: "Test",
      senderUid: ALICE_UID,
      senderName: "Kasia",
      deliveryMode: 0,
      scheduledTime: 0,
      status: "pending",
      reminderAcknowledgedTime: "",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    bobDb.collection("lists").doc(LIST_CODE).collection("items").doc("item_spoof").set({
      listCode: LIST_CODE,
      text: "Spoof",
      senderUid: ALICE_UID,
      senderName: "Kasia",
      deliveryMode: 0,
      scheduledTime: 0,
      status: "pending",
      reminderAcknowledgedTime: "",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob observes list items and updates status when answering call
  await assertSucceeds(bobDb.collection("lists").doc(LIST_CODE).collection("items").get());
  await assertSucceeds(
    bobDb.collection("lists").doc(LIST_CODE).collection("items").doc("item_1").update({
      status: "answered",
      reminderAcknowledgedTime: "12:01",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
