# Lab 1: Environment Setup — Cosmos DB Deployment

| Duration | ~15 minutes |
|----------|------------|
| **Goal** | Provision a Cosmos DB NoSQL instance and create your workshop database |

> **Important:** You will use a unique prefix (your user ID or alias) for the database name  
> to avoid collisions when sharing an account.  
> Example: if your alias is `jsmith`, your database will be `workshop_jsmith`.

---

## Prerequisites

Before starting the labs, ensure the following tools are installed and verified.

### 1. Java 17+

The project targets Java 17. Any JDK 17 or later will work (Microsoft Build of OpenJDK, Eclipse Temurin, Oracle JDK, etc.).

**Verify:**

```powershell
java -version
```

Expected (version 17 or higher):

```
openjdk version "17.0.x" ...
```

If not installed, download from: https://learn.microsoft.com/en-us/java/openjdk/download

### 2. Maven 3.8+

Maven is used to build the project and run lab demos.

**Verify:**

```powershell
mvn -version
```

Expected:

```
Apache Maven 3.8.x (or higher)
Java version: 17.x
```

If not installed, download from: https://maven.apache.org/download.cgi

> **Tip:** Confirm `mvn -version` reports a Java 17+ home directory. If it shows an older JDK, set your `JAVA_HOME` environment variable to the correct path.

### 3. Visual Studio Code

Download from: https://code.visualstudio.com/

**Required VS Code extensions:**

| Extension | Marketplace ID | Purpose |
|-----------|---------------|---------|
| Extension Pack for Java | `vscjava.vscode-java-pack` | Java language support, debugging, Maven integration |
| Azure Databases | `ms-azuretools.vscode-cosmosdb` | Browse Cosmos DB accounts, containers, and documents directly in VS Code |

**Install from the command line:**

```powershell
code --install-extension vscjava.vscode-java-pack
code --install-extension ms-azuretools.vscode-cosmosdb
```

**Verify:** Open VS Code → click the **Azure** icon in the Activity Bar (left sidebar). You should see a **Cosmos DB** section. If prompted, sign in to your Azure account.

### 4. Azure Cosmos DB Agent Kit (VS Code Extension)

The **Cosmos DB Agent Kit** provides an AI-powered GitHub Copilot agent (`@cosmosdb`) that can help you write queries, explain documents, generate code, and troubleshoot issues — all from within VS Code Chat.

**Install:**

```powershell
code --install-extension ms-azuretools.azure-cosmosdb-copilot
```

**Verify:**

1. Open VS Code and open the **GitHub Copilot Chat** panel (`Ctrl+Shift+I`)
2. Type `@cosmosdb` — you should see the Cosmos DB agent appear as an option
3. Try: `@cosmosdb What is a partition key?` to confirm it responds

> **Note:** The Agent Kit requires **GitHub Copilot** (free or paid). If you don't have Copilot, you can skip this extension — it is optional but highly recommended for the workshop.

### 5. Azure CLI (Optional)

Useful for creating databases and managing Cosmos DB from the terminal.

**Verify:**

```powershell
az --version
```

If not installed: https://learn.microsoft.com/en-us/cli/azure/install-azure-cli

### Prerequisites Checklist

| Tool | Minimum Version | Verify Command |
|------|----------------|----------------|
| Java (JDK) | 17 | `java -version` |
| Maven | 3.8 | `mvn -version` |
| VS Code | Latest | `code --version` |
| VS Code — Java Extension Pack | Latest | Extensions sidebar |
| VS Code — Azure Databases | Latest | Azure icon in sidebar → Cosmos DB section |
| VS Code — Cosmos DB Agent Kit | Latest | `@cosmosdb` in Copilot Chat |
| Azure CLI | 2.50+ (optional) | `az --version` |

---

## Choose ONE of the two options below

---

## Option A — Local Cosmos DB Emulator (Windows)

The emulator provides a free local instance of Cosmos DB for development/testing.

### Step 1: Install the Emulator

1. Download the **Azure Cosmos DB Emulator** from:  
   https://aka.ms/cosmosdb-emulator

2. Run the installer *with Admin priviledges* and follow the wizard. Default settings are fine.

3. After installation, the emulator launches automatically and opens the **Data Explorer** in your browser at:  
   ```
   https://localhost:8081/_explorer/index.html
   ```

### Step 2: Note the emulator credentials

The emulator uses well-known credentials (same for all installations):

| Setting | Value |
|---------|-------|
| **Endpoint** | `https://localhost:8081` |
| **Primary Key** | `C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==` |

### Step 3: Configure SSL for Java (Not required if using Windows Local Install)

The emulator uses a self-signed SSL certificate. Java needs to trust it. This step is requeired if using Docker installation option.

**Export the emulator certificate:**

```powershell
# The emulator cert is at:
$certPath = "$env:LOCALAPPDATA\CosmosDBEmulator\emulator.cer"
# If the file doesn't exist, export from the running emulator:
# Open Windows Certificate Manager (certmgr.msc) -> 
#   Trusted Root Certification Authorities -> Certificates ->
#   Find "DocumentDbEmulatorCertificate" -> Export as DER (.cer)
```

**Import into your JDK's truststore:**

```powershell
# Find your JDK path (adjust to your version)
$jdkPath = "C:\Program Files\Java\jdk-17"
$keytoolPath = "$jdkPath\bin\keytool.exe"
$cacertsPath = "$jdkPath\lib\security\cacerts"

# Import the certificate
& $keytoolPath -importcert -alias cosmosdb-emulator `
    -file "$env:LOCALAPPDATA\CosmosDBEmulator\emulator.cer" `
    -keystore $cacertsPath `
    -storepass changeit `
    -noprompt
```

### Step 4: Create your workshop database

1. Open the Data Explorer at `https://localhost:8081/_explorer/index.html`
2. Click **New Database**
3. Enter the Database ID: `workshop_<YOUR_USER_ID>` (e.g., `workshop_jsmith`)
4. Leave throughput unchecked (we'll set it per-container later)
5. Click **OK**

### Step 5: Update application.properties

Edit `src/main/resources/application.properties`:

```properties
cosmos.endpoint=https://localhost:8081
cosmos.key=C2y6yDjf5/R+ob0N8A7Cgv30VRDJIWEHLM+4QDU5DE2nQ9nDuVTqobD4b8mGGyPMbIZnqyMsEcaGQy67XIw/Jw==
cosmos.connection.mode=GATEWAY
cosmos.auth.type=KEY
cosmos.database=workshop_<YOUR_USER_ID>
```

> **Best Practice:** The emulator **requires Gateway connection mode**. Direct mode has known  
> SSL issues with the emulator's self-signed certificate.

---

## Option B — Shared Azure Cosmos DB Serverless Account

Your instructor has pre-created a shared Cosmos DB NoSQL account (Serverless tier).

### Step 1: Get the shared account credentials

Your instructor will provide:

| Setting | Value |
|---------|-------|
| **Account Name** | _(provided during setup)_ |
| **Endpoint** | `https://<ACCOUNT_NAME>.documents.azure.com:443/` |
| **Primary Key** | _(provided during setup)_ |

### Step 2: Create your workshop user spesific database

**Option 2a — Via Azure Portal:**

1. Navigate to the Cosmos DB account in the [Azure Portal](https://portal.azure.com)
2. Click **Data Explorer** in the left menu
3. Click **New Database**
4. Enter the Database ID: `workshop_<YOUR_USER_ID>` (e.g., `workshop_jsmith`)
5. Select **Database throughput**: Manual, leave unspecified , it will create 400 RU as default (Serverless doesn't require setting RU)
6. Click **OK**

**Option 2b — Via Azure CLI:**

```bash
# Login to Azure (if not already)
az login

# Create the database (replace placeholders)
az cosmosdb sql database create \
    --account-name <SHARED_ACCOUNT_NAME> \
    --resource-group <RESOURCE_GROUP> \
    --name workshop_<YOUR_USER_ID>
```

### Step 3: Update application.properties

Edit `src/main/resources/application.properties`:

```properties
cosmos.endpoint=https://<SHARED_ACCOUNT_NAME>.documents.azure.com:443/
cosmos.key=<SHARED_ACCOUNT_KEY>
cosmos.connection.mode=DIRECT
cosmos.auth.type=KEY
cosmos.database=workshop_<YOUR_USER_ID>
```

> Use **Serverless** for development/test workloads — pay only for consumed RUs with no minimum.

---

## Step 6: Verify the project builds

```powershell
cd cosmos-java-lab
mvn compile
```

You should see `BUILD SUCCESS`. If you have dependency issues, run:

```powershell
mvn dependency:resolve
```

---

## Concepts for Relational Developers

| Relational (SQL Server/Oracle) | Cosmos DB NoSQL |
|-------------------------------|-----------------|
| Server / Instance | Account |
| Database | Database |
| Table | Container |
| Row | Item (JSON document) |
| Column | Property |
| Primary Key | `id` + Partition Key |
| Index | Automatic indexing (all properties by default) |
| Schema | Schema-free (documents can vary) |
| Joins (tables) | Embedded documents / denormalization |
| Stored Procedures | Stored Procedures / Server-side JS |
| Throughput | Request Units (RU/s) |

---

## ✅ Checkpoint

Before proceeding to Lab 2, verify:

- [ ] Your database `workshop_<YOUR_USER_ID>` exists (visible in Data Explorer)
- [ ] `application.properties` has correct endpoint, key, and database name
- [ ] `mvn compile` succeeds
- [ ] You understand the connection mode setting (Gateway for emulator, Direct for cloud)

---

**Next:** [Lab 2 — Connectivity, Client Options & Authentication](lab-02-connectivity.md)
