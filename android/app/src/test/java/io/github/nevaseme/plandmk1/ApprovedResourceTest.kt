package io.github.nevaseme.plandmk1

import java.io.File
import io.github.nevaseme.plandmk1.core.*
import io.github.nevaseme.plandmk1.ui.*
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dynamic resource lookup is not checked by the Kotlin compiler. */
class ApprovedResourceTest {
    @Test fun everyLiteralInterfaceKeyExistsInBothLanguages() {
        val literals=Regex("""uiText\("([^"]+)"""")
        val names=Regex("""name="([^"]+)"""")
        val keys=File("src/main/java").walkTopDown().filter {it.extension=="kt"}
            .flatMap {literals.findAll(it.readText()).map {match->match.groupValues[1]}}.toSet() +
            WorkStatus.entries.map(::workLabel) + Urgency.entries.map(::urgencyLabel) + ActivityState.entries.map(::activityLabel)
        assertTrue("Expected interface sources to be inspected",keys.size>50)
        for(language in listOf("values","values-en")) {
            val available=names.findAll(File("src/main/res/$language/approved_ui.xml").readText()).map {it.groupValues[1].removePrefix("approved_")}.toSet()
            val missing=keys-available
            assertTrue("Missing $language interface strings: $missing",missing.isEmpty())
        }
    }
}
