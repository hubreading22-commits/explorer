# Minimal Kotlin SMB Connection Test

This project verifies that Kotlin can communicate with a TrueNAS SMB server using Active Directory credentials.

## Prerequisites

* JDK 21 installed

## Setup

1. Clone the project.
2. Copy `config.properties.example` to `config.properties`.
3. Update `config.properties` with your TrueNAS SMB server details, Active Directory domain, username, and password.

## How to Run

Execute the following command in your terminal:

```bash
gradlew.bat run
```

On Linux/macOS, use:

```bash
./gradlew run
```

## Expected Output

```
==============================
SMB Connection Test
==============================
Server: 192.168.1.10
Username: student1
Connecting...
✓ Connected
Authenticating...
✓ Authentication Successful
Shares:
Data
Software
Logging out...
Done.
```
