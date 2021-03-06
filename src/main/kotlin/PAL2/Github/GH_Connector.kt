package Github

import GlobalData
import PAL_DataClasses.PAL_AddonFullData
import PAL_DataClasses.initObjectMapper
import mu.KotlinLogging
import org.kohsuke.github.GHRelease
import org.kohsuke.github.GitHub
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 *
 */
private val logger = KotlinLogging.logger {}
var attempts = 0

fun connect(): GitHub
{
    val token = GlobalData.github_token
    if (token != "")
    {
        return try
        {
            GitHub.connectUsingOAuth(token)
        }
        catch (ex: Exception)
        {
            if (attempts == 3)
            {
                logger.error { "GitHub API is offline" }
                // TODO: Use fallback
            }
            attempts++
            logger.error { "Github API is likely offline, retrying in 5 seconds!" }
            Thread.sleep(5 * 1000)
            return connect()
        }
    }
    return try
    {
        GitHub.connectAnonymously()
    }
    catch (ex: Exception)
    {
        if (attempts == 3)
        {
            logger.error { "GitHub API is offline" }
            // TODO: Use fallback
        }
        attempts++
        logger.error { "Github API is likely offline, retrying in 5 seconds!" }
        Thread.sleep(5 * 1000)
        return connect()
    }
}

/**
 * Checks Repo for an addons.json and parses it.
 * Returns null if no addons.json is found.
 */
fun getAddonsFromGHRepo(repo: String): Array<PAL_AddonFullData>?
{
    val connection = connect()
    val repository = connection.getRepository(repo)
    for (a in repository.latestRelease.assets)
    {
        if (a.name == "addons.json")
        {
            val httpConnection = URL(a.browserDownloadUrl).openConnection() as HttpURLConnection
            val input = BufferedInputStream(httpConnection.inputStream)

            val om = initObjectMapper()
            return om.readValue(input, Array<PAL_AddonFullData>::class.java)
        }
    }
    return null
}

/**
 * Checking greater than 5 to keep the user with some GitHub requests in case of special cases.
 */
fun canRequest(gitHub: GitHub): Boolean
{
    return if (gitHub.rateLimit.remaining > 5)
    {
        true
    }
    else
    {
        logger.error { "Only ${gitHub.rateLimit.remaining} requests remaining; which reset at: ${gitHub.rateLimit.resetDate}" }
        false
    }
}

fun getLatestRelease(gitHub: GitHub, username:String, reponame: String): GHRelease?
{
    if (canRequest(gitHub))
    {
        return gitHub.getRepository("$username/$reponame").latestRelease
    }
    return null
}
