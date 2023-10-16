package de.fhdo.lemma.model_processing.visualizer

import de.fhdo.lemma.model_processing.asFile
import de.fhdo.lemma.service.intermediate.IntermediateMicroservice
import de.fhdo.lemma.service.intermediate.MicroserviceReference
import java.nio.file.Paths

//TODO the sourcemodel uri points to a specific service model, i.e., test.services. The importUri used in
//TODO microservice references points to the intermediate files, i.e., test.xmi. To make everything work the visualizer
//TODO currently simply gets rid of the file extension and uses "test" as prefix. However, this is not very secure
//TODO because it only works if the intermediate and the service file both have the same name.
/**
 * Builds and returns a fully qualified name of a given [IntermediateMicroservice] by adding its services filename from
 * the [this.sourceModelUri] as prefix to [this.qualifiedName].
 *
 * @author [Jonas Sorgalla](mailto:jonas.sorgalla@fh-dortmund.de)
 */
fun IntermediateMicroservice.fullyQualifiedName() : String {
    return this.sourceModelUri.asFile().nameWithoutExtension+"::"+this.qualifiedName
}

/**
 * Builds and returns a fully qualified name of a given [IntermediateMicroservice] by adding its xmi filename from
 * the [this.sourceModelUri] as prefix to [this.qualifiedName].
 *
 * @author [Jonas Sorgalla](mailto:jonas.sorgalla@fh-dortmund.de)
 */
fun MicroserviceReference.fullyQualifiedName(): String {
    return Paths.get(this.import.importUri).fileName.toString().asFile().nameWithoutExtension+"::"+this.qualifiedName
}
