const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_operator_1";
const BOB_UID = "bob_operator_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function futureDate(offsetMs = 60000) {
  return new Date(Date.now() + offsetMs);
}

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

test("Unauthenticated user: denied across user profile and all subcollections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const ts = pastDate();

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/keys`).get());
  await assertFails(
    unauthDb.doc(`users/${ALICE_UID}/keys/1645`).set({
      si: 1645,
      dateOut: "26/11/2026",
      timeOut: "16:36",
      flatUnitLocation: "M-A04-16",
      keyCardType: "Apartment Key Set",
      purpose: "Inspection",
      issuedTo: "Operator",
      qidOrNote: "",
      dateIn: "",
      timeIn: "",
      returnedBy: "",
      remarks: "",
      status: "Key issued",
      updatedAtMillis: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/visitorDays`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/maintenanceRequests`).get()
  );
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/sportsBookings`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/reminders`).get());
});

test("Owner profile: create, partial update, and guard immutable fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}`).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "operator",
      lastSignInAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Allowed profile update (only email, displayName, lastSignInAt, updatedAt)
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}`).update({
      displayName: "Alice Lead",
      lastSignInAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Reject listing /users, deleting profile, role escalation, and mutating createdAt
  await assertFails(aliceDb.collection("users").get());
  await assertFails(aliceDb.doc(`users/${ALICE_UID}`).delete());
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}`).update({
      role: "admin",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}`).update({
      createdAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});

test("Cross-user isolation: Bob cannot read or mutate Alice ledgers", async () => {
  const created = pastDate(10000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.doc(`users/${ALICE_UID}`).set({
      uid: ALICE_UID,
      email: "alice@example.com",
      displayName: "Alice",
      role: "operator",
      lastSignInAt: created,
      createdAt: created,
      updatedAt: created,
    });
    await db.doc(`users/${ALICE_UID}/keys/1634`).set({
      si: 1634,
      dateOut: "26/11/2026",
      timeOut: "14:20",
      flatUnitLocation: "M-A04-16",
      keyCardType: "Apartment Key Set",
      purpose: "Contractor",
      issuedTo: "Technician",
      qidOrNote: "294",
      dateIn: "",
      timeIn: "",
      returnedBy: "",
      remarks: "OVERDUE",
      status: "Key not yet returned",
      updatedAtMillis: Date.now(),
      createdAt: created,
      updatedAt: created,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.doc(`users/${ALICE_UID}`).get());
  await assertFails(bobDb.doc(`users/${ALICE_UID}/keys/1634`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/keys`).get());
  await assertFails(
    bobDb.doc(`users/${ALICE_UID}/keys/1634`).update({
      status: "Key returned",
      updatedAt: pastDate(),
    })
  );
  await assertFails(bobDb.doc(`users/${ALICE_UID}/keys/1634`).delete());
});

test("KeyRegister: Kotlin-mirrored create, update, invalid status, future timestamp", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/keys/1645`).set({
      si: 1645,
      dateOut: "26/11/2026",
      timeOut: "15:10",
      flatUnitLocation: "M-B02-08",
      keyCardType: "Master Key",
      purpose: "Plumbing inspection",
      issuedTo: "Kamal",
      qidOrNote: "295",
      dateIn: "",
      timeIn: "",
      returnedBy: "",
      remarks: "",
      status: "Key issued",
      updatedAtMillis: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update on return
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/keys/1645`).update({
      status: "Key returned",
      dateIn: "11/26/2026",
      timeIn: "16:36",
      returnedBy: "Kamal",
      updatedAtMillis: Date.now(),
      updatedAt: pastDate(1000),
    })
  );

  // Reject invalid status enum
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/keys/1645`).update({
      status: "Lost key",
      updatedAt: pastDate(500),
    })
  );

  // Reject mismatched doc ID vs si
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/keys/9999`).set({
      si: 1646,
      dateOut: "26/11/2026",
      timeOut: "15:10",
      flatUnitLocation: "M-B02-08",
      keyCardType: "Master Key",
      purpose: "Inspection",
      issuedTo: "Kamal",
      qidOrNote: "",
      dateIn: "",
      timeIn: "",
      returnedBy: "",
      remarks: "",
      status: "Key issued",
      updatedAtMillis: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future server timestamp spoofing
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/keys/1647`).set({
      si: 1647,
      dateOut: "26/11/2026",
      timeOut: "15:10",
      flatUnitLocation: "M-B02-08",
      keyCardType: "Master Key",
      purpose: "Inspection",
      issuedTo: "Kamal",
      qidOrNote: "",
      dateIn: "",
      timeIn: "",
      returnedBy: "",
      remarks: "",
      status: "Key issued",
      updatedAtMillis: Date.now(),
      createdAt: futureDate(),
      updatedAt: futureDate(),
    })
  );
});

test("VisitorDays, Maintenance, SportsBookings, Reminders: valid payloads & bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = pastDate(10000);

  // VisitorDay
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/visitorDays/2026-11-26`).set({
      dateIso: "2026-11-26",
      displayDate: "26-Nov-2026",
      monthKey: "2026-11",
      visitorCount: 8,
      viewingCount: 2,
      flatDetails: "M-C13-16, M-C15-21",
      notes: "",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/visitorDays/2026-11-26`).update({
      visitorCount: -1,
      updatedAt: pastDate(1000),
    })
  );

  // MaintenanceRequest
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/maintenanceRequests/1`).set({
      sn: 1,
      date: "26/11/2026",
      names: "Tariq",
      phoneNumber: "+97455550000",
      workOrderNumber: "WO-2026-1190",
      qidNumber: "289",
      flatDetails: "M-A01-02",
      requestDetails: "AC thermostat check",
      createdAt: created,
      updatedAt: created,
    })
  );

  // SportsBooking (without optional attachmentPath, then with attachmentPath)
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/sportsBookings/1`).set({
      sn: 1,
      sport: "PADEL",
      date: "25/11/2026",
      time: "18:00 - 19:00",
      name: "Yusuf",
      unitDetails: "M-C10-05",
      discount: "FREE",
      referenceNo: "PD-101",
      durationPlayed: 1.0,
      remarks: "Verified resident",
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/sportsBookings/2`).set({
      sn: 2,
      sport: "TENNIS",
      date: "25/11/2026",
      time: "18:00",
      name: "Yusuf",
      unitDetails: "M-C10-05",
      discount: "FREE",
      referenceNo: "PD-102",
      durationPlayed: 1.0,
      remarks: "",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Move-In Keys Given to Client + issuedBy
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/keys/1638`).set({
      si: 1638,
      dateOut: "11/26/2026",
      timeOut: "08:38AM",
      flatUnitLocation: "M-E25-34",
      keyCardType: "2 Keys",
      purpose: "Move in",
      issuedBy: "Mr. Shajith",
      issuedTo: "Pending T-code",
      qidOrNote: "T-code follow-up required from Mr. Shajith",
      dateIn: "",
      timeIn: "",
      returnedBy: "",
      remarks: "Move-In Keys Given to Client",
      status: "Move-In Keys Given to Client",
      updatedAtMillis: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // OpsReminder (uses `done`, omits null optional fields, includes DAILY_1530 / report types)
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/reminders/daily_0740`).set({
      id: "daily_0740",
      type: "DAILY_0740",
      title: "Morning Sports & Key Check",
      message: "Verify previous day bookings",
      scheduledAtMillis: Date.now(),
      done: false,
      checklistMask: 0,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/reminders/daily_1530`).set({
      id: "daily_1530",
      type: "DAILY_1530",
      title: "03:30 PM Daily Briefing",
      message: "Daily briefing",
      scheduledAtMillis: Date.now(),
      done: false,
      checklistMask: 0,
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/reminders/daily_0740`).update({
      done: true,
      checklistMask: 15,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    aliceDb.doc(`users/${ALICE_UID}/reminders/bad_type`).set({
      id: "bad_type",
      type: "UNKNOWN",
      title: "Bad",
      message: "",
      scheduledAtMillis: Date.now(),
      done: false,
      checklistMask: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // InspectionReport & ReportDispatch owner rules
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/inspectionReports/insp_1`).set({
      id: "insp_1",
      buildingName: "Building A04",
      inspectionDate: "27/11/2026",
      inspectorName: "Emmanuel Ssali",
      itemCount: 3,
      summaryNote: "Basement & rooftop corridor checked",
      localFilePath: "/Documents/Masaken Mesaimeer/Inspections/Bldg_A04.xlsx",
      driveFileId: "drive_123",
      createdAtMillis: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );
  await assertSucceeds(
    aliceDb.doc(`users/${ALICE_UID}/reportDispatches/disp_1`).set({
      id: "disp_1",
      reportType: "WEEKLY",
      subject: "Weekly keys log report; Week 48 ( 20 Nov to 26 Nov)",
      periodLabel: "20 Nov to 26 Nov 2026",
      recipientsTo: "pm@example.com",
      recipientsCc: "ops@example.com",
      attachmentNames: "WSF-PMD-F-09.xlsx",
      localPaths: "/Documents/Masaken Mesaimeer/Weekly/WSF-PMD-F-09.xlsx",
      driveStatus: "Uploaded",
      dispatchChannel: "GMAIL_API",
      sentAtMillis: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection(`users/${ALICE_UID}/inspectionReports`).get());
  await assertFails(bobDb.collection(`users/${ALICE_UID}/reportDispatches`).get());
});

test("Client queries alignment: owner list & ordered queries succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/keys`).orderBy("si", "desc").get()
  );
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/visitorDays`)
      .orderBy("dateIso", "desc")
      .get()
  );
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/maintenanceRequests`)
      .orderBy("sn", "desc")
      .get()
  );
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/sportsBookings`)
      .orderBy("sn", "desc")
      .get()
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reminders`).get()
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/inspectionReports`).orderBy("createdAtMillis", "desc").get()
  );
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/reportDispatches`).orderBy("sentAtMillis", "desc").get()
  );
});
