package de.fhdo.lemma.model_processing.visualizer.languages

import de.fhdo.lemma.ServiceDslStandaloneSetup
import de.fhdo.lemma.model_processing.annotations.LanguageDescriptionProvider
import de.fhdo.lemma.model_processing.languages.LanguageDescription
import de.fhdo.lemma.model_processing.languages.LanguageDescriptionProviderI
import de.fhdo.lemma.model_processing.languages.XmiLanguageDescription
import de.fhdo.lemma.model_processing.languages.XtextLanguageDescription
import de.fhdo.lemma.service.ServicePackage
import org.jetbrains.annotations.Nullable
import de.fhdo.lemma.service.intermediate.IntermediatePackage as IntermediateServicePackage


/**
 * [LanguageDescriptionProvider] for the visualizer as expected by LEMMA's model processing framework.
 *
 * @author [Jonas Sorgalla](mailto:jonas.sorgalla@fh-dortmund.de)
 */
@Suppress("unused")
@LanguageDescriptionProvider
internal class DescriptionProvider : LanguageDescriptionProviderI {

    @Nullable
    override fun getLanguageDescription(
        forLanguageNamespace: Boolean, forFileExtension: Boolean,
        languageNamespaceOrFileExtension: String
    ): LanguageDescription? {
        return when (languageNamespaceOrFileExtension) {
            "services" -> XtextLanguageDescription(ServicePackage.eINSTANCE, ServiceDslStandaloneSetup())
            IntermediateServicePackage.eNS_URI -> XmiLanguageDescription(IntermediateServicePackage.eINSTANCE)
            de.fhdo.lemma.data.intermediate.IntermediatePackage.eNS_URI -> XmiLanguageDescription(de.fhdo.lemma.data.intermediate.IntermediatePackage.eINSTANCE)
            else -> null
        }
    }
}
