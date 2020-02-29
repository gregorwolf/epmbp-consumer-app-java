# EPM Business Partner - Consumer App Java

This example application is still work in progress. It should demonstrate how to use a custom built OData Service with the SAP Cloud SDK for Java and the SAP Cloud Application Programming Model. And that with authentication and principal propagation. So that the service in the ABAP Backend is called not with a technical user, but with the user that was authenticated in the SAP Cloud Platform.

## Run local

To run against the local mock service [epmbp-mock-service](https://github.com/gregorwolf/epmbp-mock-service) you have to create a file named *default-env.json* with the following content:

```
{
  "VCAP_SERVICES": {
  },
  "destinations": [
    {
      "name": "NPL",
      "url": "http://localhost:3000/v2"
    }
  ]
}
```

Then execute:

`npm run setup`

Followed by:

`npm start`
