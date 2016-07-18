# Salesforce Webhook Creator

This is a simple web app that makes it easy to create Webhooks on Salesforce.  For usage info see: http://www.jamesward.com/2014/06/30/create-webhooks-on-salesforce-com

Either use a shared instance of this app: https://salesforce-webhook-creator.herokuapp.com/

Or deploy your own instance on Heroku:

1. Create a new Connected App in Salesforce:

    1. [Create a Connected App](https://login.salesforce.com/app/mgmt/forceconnectedapps/forceAppEdit.apexp)
    1. Check `Enable OAuth Settings`
    1. Set the `Callback URL` to `http://localhost:9000/_oauth_callback`
    1. In `Available OAuth Scopes` select `Full access (full)` and click `Add`
    1. Save the new Connected App and keep track of the Consumer Key & Consumer Secret for later use

1. Deploy this app on Heroku: [![Deploy](https://www.herokucdn.com/deploy/button.svg)](https://heroku.com/deploy)
1. Edit the Connected App on Salesforce and update the `Callback URL` to be `https://YOUR_APP_NAME.herokuapp.com/_oauth_callback`
