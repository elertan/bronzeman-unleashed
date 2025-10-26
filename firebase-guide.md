# Firebase Setup Guide

> **Disclaimer:** This guide is provided to help configure the plugin. We are not responsible for any data loss, security issues, or charges incurred through Firebase usage. Use Firebase at your own risk.

This guide explains how to create a Firebase Realtime Database that your group can use with the plugin.
## Step 1: Open the Firebase Console  
Go to [https://console.firebase.google.com/](https://console.firebase.google.com/) and sign in with your Google account.  
If you do not have a Google account, you can create one for free.

## Step 2: Create a New Project  
Once you are logged in, click **“Create a new project.”**  
Give your project a name that others **will not easily guess**. This name will be part of the project’s URL and helps protect your group’s data.

### Our recommendation
Use a random sequence of letters and numbers for the project name — [generate one here](https://www.random.org/strings/?num=1&len=30&digits=on&loweralpha=on&unique=on&format=plain&rnd=new)

## Step 3: Access the Realtime Database  
After the project is created, look for **“Build” → “Realtime Database”** in the menu on the left, and click it.  
Then, click **“Create Database.”**

## Step 4: Choose a Region  
Select the region closest to you or your group members.  
A nearby region makes the database respond faster. Click **“Next.”**

## Step 5: Enable the Database  
When asked about security mode, click **“Enable.”**  
The initial security mode does not matter because you will replace the rules in the next step.

## Step 6: Update the Rules  
In the Realtime Database view, switch to the **Rules** tab.  
You will see some text that defines who can read and write data.  
Delete everything in that box and replace it with this:

```json
{
  "rules": {
    ".read": true,
    ".write": true
  }
}
```

Then click **“Publish.”**

## Step 7: Copy the Database URL  
Once the rules are published, return to the **Data** tab. At the top, you will see your database’s reference URL — it looks something like this:

```
https://your-project-name-default-rtdb.firebaseio.com/
```

Copy this URL using the link button on the left. You will need to paste it into the plugin’s configuration later.

Only share this URL with others you trust, as anyone with the URL can read or delete the data.

## Step 8: Final Check  
Before closing Firebase:  
- Make sure the rules are published.  
- Copy your database URL safely.  
- Confirm that the database type is **Realtime Database** (not Firestore or Storage).

You’re done! Your Firebase database is now ready to connect to the plugin.