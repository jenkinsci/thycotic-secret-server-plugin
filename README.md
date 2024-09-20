# Delinea Secret Server

[![Jenkins Plugin Build](https://github.com/jenkinsci/thycotic-secret-server-plugin/actions/workflows/package.yml/badge.svg)](https://github.com/jenkinsci/thycotic-secret-server-plugin/actions/workflows/package.yml)

The Delinea Secret Server Jenkins Plugin allows you to access and reference your Secret Server secrets for use in Jenkins builds.

## Usage

1. This plugin add the ability to include Secret Server Secrets into your build environment.

![build-environment](images/jenkins-build-environment.jpg)

This is allows you to include the `Base URL` of you Secret Server and `Secret ID` you wish to access.

Additionally you will need to include a valid credential provider.

![add-credential](images/jenkins-credential-provider.jpg)

You will now have the option to change the `kind` of credential you wish to add, to that of a `SecretServer User Credentials`.

After you have added your credentials to the build environment you can can use the secret in your build/s.

> IMPORTANT: By default, this plugin will add a `TSS_` prefix to the environment variables. You should leave the `Environment Variable Prefix` field blank in the Jenkins UI when consuming your credential.


2. This plugin add the ability to include Secret Server Secrets into global credentials.

![add-Secret-Server-vault-credential](images/jenkins-vault-credential-provider.jpg)

1. Create Credentials: 
Create a `Secret Server user credentials` that contains the Secret Server service account credentials.

2. Configure Credentials: 
Enter the `Vault URL, Secret ID,` and select the previously created Secret Server user credential in the `Credential ID` field.
> Note: that the Username and Password fields are read-only.

3. Test Connection: 
After filling in the required fields, click the `Test Connection` button.
If all inputs are correct, a `Connection Successful` message will appear. Otherwise, an error message will be displayed.

4. Create and Fetch Secrets: 
Once the connection test is successful, click `Create` to fetch the secret from Secret.
The fetched secret will include the username and password.

