# Delinea Secret Server

[![Jenkins Plugin Build](https://github.com/jenkinsci/thycotic-secret-server-plugin/actions/workflows/package.yml/badge.svg)](https://github.com/jenkinsci/thycotic-secret-server-plugin/actions/workflows/package.yml)

The Delinea Secret Server Jenkins Plugin allows you to securely access and reference secrets stored in Secret Server for use in Jenkins builds. It also enables you to integrate Secret Server secrets into Jenkins Global Credentials, making them easily reusable.

For more information, please refer to the [Delinea documentation](https://docs.delinea.com/online-help/integrations/jenkins/configure-jenkins.htm) .

## Usage

### 1. Add Secrets to Build Environment

This plugin adds the ability to include Secret Server Secrets into your Jenkins build environment.

![build-environment](images/jenkins-build-environment.jpg)

This is allows you to include the `Base URL` of you Secret Server and `Secret ID` you wish to access.

Additionally you will need to include a valid credential provider.

![add-credential](images/jenkins-credential-provider.jpg)

You will now have the option to change the `kind` of credential you wish to add, to that of a `SecretServer User Credentials`.

After you have added your credentials to the build environment you can can use the secret in your build/s.

> IMPORTANT: By default, this plugin will add a `TSS_` prefix to the environment variables. You should leave the `Environment Variable Prefix` field blank in the Jenkins UI when consuming your credential.

---

### 2. Add Secrets to Global Credentials

This plugin add the ability to include Secret Server Secrets to Jenkins **Global Credentials**.

![add-Secret-Server-vault-credential](images/jenkins-vault-credential-provider.jpg)

#### Step 1: Create Credentials 
Create a `Secret Server user credentials` that contains the Secret Server application account credentials.

#### Step 2: Configure Credentials

- **Enter the `Username slug name`**:  
  Provide the slug name of the secret template associated with the secret you want to retrieve from Secret Server.  
  _Examples: `username`, `client-id`_

- **Enter the `Password slug name`**:  
  Provide the slug name of the secret template associated with the secret you want to retrieve from Secret Server.  
  _Examples: `password`, `client-secret`_

> **Note**: If you're using a non-standard secret template, provide the **custom** slug names here.

- **Enter the `Vault URL`, `Secret ID`,** and select the previously created Secret Server credential in the `Credential ID` field.

> **Note**: The `Username` and `Password` fields are read-only.

#### Step 3: Test Connection
After filling in the required fields, click the **Test Connection** button.  
If all inputs are correct, a `Connection Successful` message will appear. Otherwise, an error message will indicate what needs to be fixed.

#### Step 4: Create and Fetch Secrets
Once the connection test is successful, click **Create** to fetch the secret from Secret Server.  
The fetched secret will include the username and password.
