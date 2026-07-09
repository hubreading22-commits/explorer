import com.smbcore.SmbClient
import com.smbcore.config.SmbConfig
import com.smbcore.model.Credentials
import com.smbcore.model.SmbResult
import java.io.File
import java.io.FileInputStream
import java.util.Properties

fun main() {
    println("==============================")
    println("SMB SDK Architecture Test")
    println("==============================")

    val configFile = File("config.properties")
    if (!configFile.exists()) {
        println("Error: config.properties not found.")
        return
    }

    val props = Properties()
    FileInputStream(configFile).use { props.load(it) }

    val server = props.getProperty("server")
    val domain = props.getProperty("domain", "")
    val username = props.getProperty("username")
    val password = props.getProperty("password", "")

    if (server.isNullOrBlank() || username.isNullOrBlank()) {
        println("Error: server and username must be provided in config.properties")
        return
    }

    val config = SmbConfig(
        serverIP = server,
        bufferSize = 4096,
        connectTimeout = 10000,
        readTimeout = 10000,
        writeTimeout = 10000
    )

    // Instantiate our shiny new SDK entry point!
    val smb = SmbClient.create(config)
    
    val creds = Credentials(username, password.toCharArray(), domain)

    println("Logging in as $username...")
    when (val loginResult = smb.login(creds)) {
        is SmbResult.Failure -> {
            println("Login Failed: ${loginResult.error}")
            return
        }
        is SmbResult.Success -> {
            println("✓ Logged in successfully. Current User: ${loginResult.data.username}")
        }
    }

    val targetShare = "private"
    println("\nListing directories in '$targetShare' via SDK...")
    
    var fileToStream: String? = null
    var folderToStreamFrom: String? = null

    when (val dirResult = smb.listDirectory(targetShare, "")) {
        is SmbResult.Failure -> println("Failed to list directory: ${dirResult.error}")
        is SmbResult.Success -> {
            for (item in dirResult.data) {
                val typeLabel = if (item.isDirectory) "DIR " else "FILE"
                println(String.format("%-4s | %-30s | %d bytes", typeLabel, item.name, item.size))
                
                if (item.isDirectory && folderToStreamFrom == null) {
                    folderToStreamFrom = item.name
                }
            }
        }
    }

    if (folderToStreamFrom != null) {
        println("\nExploring folder: $folderToStreamFrom")
        when (val subDirResult = smb.listDirectory(targetShare, folderToStreamFrom)) {
            is SmbResult.Failure -> println("Failed to list sub-directory: ${subDirResult.error}")
            is SmbResult.Success -> {
                for (item in subDirResult.data) {
                    if (!item.isDirectory && fileToStream == null) {
                        fileToStream = item.name
                    }
                }
            }
        }

        if (fileToStream != null) {
            val remotePath = "$folderToStreamFrom\\$fileToStream"
            println("\nTesting streaming via SDK for: $remotePath")
            
            when (val streamResult = smb.openFile(targetShare, remotePath)) {
                is SmbResult.Failure -> println("Failed to open file stream: ${streamResult.error}")
                is SmbResult.Success -> {
                    val stream = streamResult.data
                    println("Opened stream for file of total length: ${stream.length()} bytes")
                    
                    val buffer = ByteArray(4096)
                    val bytesRead = stream.read(buffer)
                    println("✓ SDK successfully read $bytesRead bytes!")
                    
                    stream.close()
                }
            }
        } else {
            println("No files found in $folderToStreamFrom to stream.")
        }
    }

    println("\nLogging out via SDK...")
    smb.logout()
    println("Done.")
}
