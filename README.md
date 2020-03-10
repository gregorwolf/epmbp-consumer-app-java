# EPM Business Partner - Consumer App Java

This example application is still work in progress. It should demonstrate how to use a custom built OData Service with the SAP Cloud SDK for Java and the SAP Cloud Application Programming Model. And that with authentication and principal propagation. So that the service in the ABAP Backend is called not with a technical user, but with the user that was authenticated in the SAP Cloud Platform.

With the help of [Marcel Merkle](https://people.sap.com/marcelmerkle) who answered the question [JWT not recognized by xsuaa-spring-boot-starter](https://answers.sap.com/answers/12996856/view.html) the authenticated call to the Java backend is now working. The on premise backend call using the approuter also works. But when using the same destination with the SAP Cloud SDK it seems that the JWT isn't passed to the backend.

## Run local

To run against the local mock service [epmbp-mock-service](https://github.com/gregorwolf/epmbp-mock-service) you have to set the environment variable destinations:

```bash
#!/bin/bash
export destinations='[{name: "NPL", url: "http://localhost:3000"}]'
```

The authentication data is maintained in the file **srv/src/main/resources/application.yaml**

Then execute:

`npm run setup`

Followed by:

`npm start`

## Deploy to SAP Cloud Platform - Cloud Foundry

### Prerequisite

- You have a [SAP Cloud Platform Trial account](https://hanatrial.ondemand.com/)
- The [Cloud MTA Build Tool (MBT)](https://sap.github.io/cloud-mta-build-tool/) is installed
- The [Cloud Foundry command line tool](https://docs.cloudfoundry.org/cf-cli/install-go-cli.html) is installed
- The [MultiApps CF CLI Plugin](https://github.com/cloudfoundry-incubator/multiapps-cli-plugin) is installed
- You've connected using `cf login` to your trial account
- You've connected a SAP Cloud Connector to your subaccount
- Principal Propagation is setup in the Cloud Connector to the ABAP Backend

### Preperation

Before you can deploy the application to your Cloud Foundry account two destinations must be created. Please find here what I've used in my environment:

Destination NPL used by the approuter and SAP Cloud SDK:

```
URL=http\://npl752.virtual\:44300
Name=NPL
ProxyType=OnPremise
Type=HTTP
sap-client=001
Authentication=PrincipalPropagation
```

### Build

`npm run build:cf`

### Deploy

`npm run deploy:cf`
