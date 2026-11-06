package io.github.nevaseme.plandmk1

import io.github.nevaseme.plandmk1.cloud.*
import io.github.nevaseme.plandmk1.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class GroupRenameApiTest {
    @Test fun renameUsesExistingGroupCredentialAndReturnsSameRoster()=runBlocking {
        val me=UUID.randomUUID().toString();val group=UUID.randomUUID().toString()
        val session=CloudSession("https://example.invalid",group,me,me,"0".repeat(64),MemberRole.ADMIN,"Original")
        val roster=MemberRoster(groupId=group,members=listOf(GroupMember(me,"Host",MemberRole.ADMIN,listOf(me))))
        val expected=CloudRosterEnvelope(group,1,roster,listOf(CloudRosterSnapshot(1,roster)),"Archive")
        val api=CloudHttpApi(OkHttpClient.Builder().addInterceptor {chain->
            val request=chain.request()
            assertEquals("/api/v1/groups/$group/name",request.url.encodedPath)
            assertEquals("Bearer ${session.token}",request.header("Authorization"))
            val buffer=Buffer();request.body!!.writeTo(buffer)
            assertEquals("Archive",Json.parseToJsonElement(buffer.readUtf8()).jsonObject["groupName"]!!.jsonPrimitive.content)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(Json.encodeToString(expected).toResponseBody()).build()
        }.build())
        assertEquals(expected,api.renameGroup(session,"Archive"))
        assertEquals("Original",session.groupName)
    }
}
