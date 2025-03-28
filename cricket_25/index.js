const functions = require("firebase-functions");
const admin = require("firebase-admin");

// Initialize Firebase Admin SDK
admin.initializeApp();

// Simple hello world function for testing
exports.helloWorld = functions.https.onRequest((request, response) => {
  functions.logger.info("Hello logs!", {structuredData: true});
  response.send("Hello from Firebase!");
});

// Add a Cloud Function to handle notifications from CloudNotifications node
exports.processCloudNotification = functions.https.onRequest(async (request, response) => {
  try {
    functions.logger.info("Processing cloud notification", {structuredData: true});

    // Get the notification data from the request
    const notificationData = request.body;

    // Basic validation
    if (!notificationData || !notificationData.contentType || !notificationData.contentId) {
      functions.logger.error("Invalid notification data", {data: notificationData});
      response.status(400).send({error: "Invalid notification data"});
      return;
    }

    // Send push notification to all users subscribed to the relevant topic
    const message = {
      notification: {
        title: notificationData.title || "Trending Content",
        body: notificationData.message || "Check out what's trending!"
      },
      data: {
        contentType: notificationData.contentType,
        contentId: notificationData.contentId,
        team: notificationData.team || ""
      },
      topic: notificationData.team ? `team_${notificationData.team}` : "trending"
    };

    // Send the message
    const result = await admin.messaging().send(message);
    functions.logger.info("Successfully sent message:", result);

    response.status(200).send({success: true, messageId: result});
  } catch (error) {
    functions.logger.error("Error sending notification:", error);
    response.status(500).send({error: error.message});
  }
});

// Listen for writes to CloudNotifications node and send FCM messages
exports.sendTrendingNotification = functions.database.ref('/CloudNotifications/{pushId}')
  .onCreate(async (snapshot, context) => {
    try {
      const notificationData = snapshot.val();

      if (!notificationData || !notificationData.contentType || !notificationData.contentId) {
        functions.logger.error("Invalid notification data", {data: notificationData});
        return null;
      }

      // Create message payload
      const message = {
        notification: {
          title: notificationData.title || "Trending Content",
          body: notificationData.message || "Check out what's trending!"
        },
        data: {
          contentType: notificationData.contentType,
          contentId: notificationData.contentId,
          team: notificationData.team || ""
        },
        topic: notificationData.team ? `team_${notificationData.team}` : "trending"
      };

      // Send message
      const result = await admin.messaging().send(message);
      functions.logger.info("Successfully sent message:", result);

      return result;
    } catch (error) {
      functions.logger.error("Error sending notification:", error);
      return null;
    }
  });