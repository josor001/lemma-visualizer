package de.fhdo.lemma.model_processing.visualizer.graphviz

import de.fhdo.lemma.data.intermediate.IntermediateImport
import de.fhdo.lemma.model_processing.annotations.CodeGenerationModule
import de.fhdo.lemma.model_processing.asXmiResource
import de.fhdo.lemma.model_processing.builtin_phases.code_generation.AbstractCodeGenerationModule
import de.fhdo.lemma.model_processing.visualizer.fullyQualifiedName
import de.fhdo.lemma.model_processing.visualizer.graphviz.commandline.ModuleCommandLine
import de.fhdo.lemma.model_processing.visualizer.graphviz.commandline.ModuleCommandLine.intermediatePath
import de.fhdo.lemma.model_processing.visualizer.graphviz.exceptions.ModuleException
import de.fhdo.lemma.model_processing.visualizer.model.*
import de.fhdo.lemma.service.intermediate.*
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Graphviz
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultDirectedGraph
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.Charset
import java.nio.file.Paths

/**
 * CodeGenerationModule which is invoked by the lemma model processor to
 * create a visualization of the given service models.
 *
 * @author [Jonas Sorgalla](mailto:jonas.sorgalla@fh-dortmund.de)
 */
/* TODO ISSUE DOTExporter of JGraphT does not allow Subgraphs
* which would be needed to model e.g. interfaces as seperate entities
* Possible way to fix this is to exchange JGraphT or change the visualization.
* https://stackoverflow.com/questions/57820898/creating-graph-with-clusters-using-jgrapht
*/
@CodeGenerationModule(name = "ServicesToGraphVizGenerator")
class GenerationModuleMicroserviceNames : AbstractCodeGenerationModule() {

    /**
     * The graph which is populated during model processing and later on can be used to generate an image.
     */
    private val microserviceGraph: Graph<MicroserviceVertex, MicroserviceEdge> =
        DefaultDirectedGraph(MicroserviceEdge::class.java)

    private val visitedMicroservices: MutableMap<String, MicroserviceVertex> = mutableMapOf()
    private val discoveredEdges: MutableList<Pair<String, String>> = mutableListOf()
    private val processedServiceModels: MutableList<URI> = mutableListOf()

    override fun getLanguageNamespace(): String {
        return IntermediatePackage.eNS_URI;
    }

    override fun execute(
        phaseArguments: Array<String>,
        moduleArguments: Array<String>
    ): Map<String, Pair<String, Charset>> {
        with(ModuleCommandLine) {
            try {
                invoke(moduleArguments)
            } catch (ex: java.lang.Exception) {
                throw ModuleException(ex.message)
            }
        }

        println("### LEMMA SYSTEM MODEL VISUALIZATION ###")
        //If example mode is set from CLI, skip regular generation and only generate test graph
        if(ModuleCommandLine.example) {
            println("# EXAMPLE MODE")
            println("skipping all actual model processing...")
            createExampleMicroserviceGraph()
        } else {
            //PRODUCTION MODE, example graph is skipped
            //Initial parsing run where the intermediateModelResource from the code generation phase
            //is added to the graph
            val initialModel: IntermediateServiceModel = (resource.contents[0] as IntermediateServiceModel)

            println("InitialModel "+initialModel.sourceModelUri)

            // Populates the Microservice Graph with values from initialModel
            println("Populating graph with initial model...")
            populateMicroserviceGraph(initialModel)
            // if there are service files which were not reached through the recursion
            // in createMicroserviceGraph() those are treated here
            ModuleCommandLine.models?.forEach {
                val str = Paths.get(ModuleCommandLine.intermediatePath.toString(), it.toString()).toString()
                val resource = str.asXmiResource()
                val model = resource.contents[0] as IntermediateServiceModel
                if (!processedServiceModels.contains(URLEncoder.encode(model.sourceModelUri, "utf-8"))) {
                    println("Populating graph with additional intermediate model...")
                    populateMicroserviceGraph(model)
                }
            }
        }

        //Draws the discovered edges during the populateGraph stage.
        //Currently, only required microservices of a microservice are discovered as edges.
        println("Drawing edges...")
        drawEdges()
        //
        val targetFilePath: String = targetFolder + File.separator + "system_model.dot"
        val resultFiles: MutableMap<String, String> = HashMap()
        println("Creating DOT representation of graph...")
        resultFiles[targetFilePath] = createDotRepresentation(ModuleCommandLine.detailLevel)
        // create image in target
        println("Creating graphical representation of graph...")
        createImageRepresentation(
            ImageConfig(height = ModuleCommandLine.height, format = Format.PNG),
            File(targetFolder + File.separator + "system_model.png"),
            ModuleCommandLine.detailLevel
        )
        println("Success!")
        println("Generated artifacts can be found at $targetFolder")

        return withCharset(resultFiles, java.nio.charset.StandardCharsets.UTF_8.name())
    }

    private fun drawEdges() {
        discoveredEdges.forEach {
            microserviceGraph.addEdge(
                visitedMicroservices.get(it.first),
                visitedMicroservices.get(it.second),
                MicroserviceEdge("requires")
            )
        }


    }

    /**
     * Create a graph of MicroserviceVertex and MicroserviceEdges objects based on the modelRoot.
     *
     * @return a graph based on MicroserviceVertex objects.
     */
    private fun populateMicroserviceGraph(modelRoot : IntermediateServiceModel) {
        if (!processedServiceModels.contains(URLEncoder.encode(modelRoot.sourceModelUri, "utf-8"))) {
            // mark as already processed
            processedServiceModels.add(URI(URLEncoder.encode(modelRoot.sourceModelUri, "utf-8") ))
            //traverse services of model file
            modelRoot.microservices.forEach {
                visitMicroserviceVertex(it)
            }
            // iterative parsing of discovered imports
            modelRoot.imports.filter { it.importTypeName == "MICROSERVICES" }.forEach {
                recursiveImportProcessing(it)
            }
        }
    }

    private fun recursiveImportProcessing(intermediateImport: IntermediateImport) {
        //println(File(absoluteIntermediateModelFilePath+initialModel.sourceModelUri).canonicalPath)
        var importStr = ""
        if(!File(intermediateImport.importUri).isAbsolute) {
            //TODO this can definitely be implemented in a better way
            importStr = Paths.get(
                intermediatePath.toString(), Paths.get(intermediateImport.importUri).fileName.toString()
            ).toFile().absolutePath
        } else {
            Paths.get(importStr).fileName.toString()
        }
        val resource = importStr.asXmiResource()

        val model = resource.contents.get(0) as IntermediateServiceModel
        if (!processedServiceModels.contains(URLEncoder.encode(model.sourceModelUri, "utf-8"))) {
            model.microservices.forEach {
                visitMicroserviceVertex(it)
            }
            processedServiceModels.add(URI(URLEncoder.encode(model.sourceModelUri, "utf-8")))
            model.imports.filter { it.importTypeName == "MICROSERVICES" }.forEach {
                // traverse each microservice import
                recursiveImportProcessing(it)
            }
        }
    }

    private fun visitMicroserviceVertex(service: IntermediateMicroservice): MicroserviceVertex? {
        //if the service has not been added as a vertex
        if (!visitedMicroservices.contains(service.qualifiedName)) {
            val newVertex =
                MicroserviceVertex(service.visibility, service.name, service.fullyQualifiedName(), service.type)
            visitedMicroservices[service.fullyQualifiedName()] = newVertex
            // remember edges which need to be drawn later
            service.requiredMicroservices.forEach {
                discoveredEdges.add(buildMicroserviceEdge(service.fullyQualifiedName(), it))
            }
            if (!service.interfaces.isEmpty()) {
                service.interfaces.forEach {
                    newVertex.interfaces.add(buildInterfaceVertex(it))
                }
            }
            microserviceGraph.addVertex(newVertex)
            return newVertex
        } else {
            // If the service has already been added but might differ in required microservices or interfaces.
            // this might happen if a service is modeled multiple times in different LEMMA files.
            // E.g. Team A and B might require and, thus, model a service owned by Team C. However, they might depend
            // on different interface or other services.
            // In practice this is unlikely as teams (hopefully) use a central model repository
            // to store and exchange models.
            var existingVertex = microserviceGraph.vertexSet().find { vertex -> vertex.qualifiedName == service.fullyQualifiedName() }

            if (existingVertex != null) {
                // checking for not yet added interfaces; set used to improve performance
                val vertexInterfaceNames = existingVertex.interfaces.map { vertexInterface -> vertexInterface.name }.toSet()
                val serviceInterfaceNames = service.interfaces.map { intermediateInterface -> intermediateInterface.name }.toSet()

                // identify possible new interfaces
                serviceInterfaceNames.minus(vertexInterfaceNames).forEach {
                    // for each newly discovered interface of an existing vertex, add the interface as new subvertex to it.
                    val newAdd = service.interfaces.find { intermediateInterface ->  intermediateInterface.name == it}
                    if(newAdd != null)
                        existingVertex.interfaces.add(buildInterfaceVertex(newAdd))
                }

                // identify possible new edges
                service.requiredMicroservices.forEach {
                    val possibleEdge = buildMicroserviceEdge(service.fullyQualifiedName(), it)
                    if(!discoveredEdges.contains(possibleEdge)) {
                        // if the discovered edge is not set yet, it is added now
                        discoveredEdges.add(possibleEdge)
                    }
                }
            }

            return existingVertex
        }
    }

    private fun buildMicroserviceEdge(serviceName: String, it: MicroserviceReference): Pair<String, String> {
        return if (it.isImported) {
            Pair(serviceName, it.fullyQualifiedName())
        } else {
            Pair(serviceName, it.localMicroservice.fullyQualifiedName())
        }
    }

    private fun buildInterfaceVertex(it: IntermediateInterface): InterfaceSubVertex {
        val subVertex = InterfaceSubVertex(it.name, it.visibility)
        it.operations.forEach { intermediateOperation ->
            val op = OperationSubVertex(intermediateOperation.name, intermediateOperation.visibility)
            intermediateOperation.parameters.forEach {
                val param = ParameterSubVertex(it.communicationType, it.name, it.type.name)
                op.parameters.add(param)
            }
            subVertex.operations.add(op)
        }
        return subVertex
    }

    /**
     * Create a toy graph based on MicroserviceVertex objects.
     * Context is inspired by the sock shop microservice demo application.
     *
     * @return a graph based on MicroserviceVertex objects.
     */
    private fun createExampleMicroserviceGraph() {
        println("creating example graph...")
        val orders = MicroserviceVertex("+", "orders", "sockshop.orders", "FUNCTIONAL", "Java")
        val catalogue = MicroserviceVertex("+", "catalogue", "sockshop.catalogue", "FUNCTIONAL", "Go")
        val shipping = MicroserviceVertex("+", "shipping", "sockshop.shipping", "FUNCTIONAL", "Java")
        val carts = MicroserviceVertex("+", "carts", "sockshop.carts", "FUNCTIONAL", "Java")
        val user = MicroserviceVertex("+", "user", "sockshop.user", "FUNCTIONAL", "Go")
        val queuemaster = MicroserviceVertex("+", "queue-master", "sockshop.queue-master", "INFRASTRUCTURE", "Java")
        microserviceGraph.addVertex(orders)
        microserviceGraph.addVertex(catalogue)
        microserviceGraph.addVertex(shipping)
        microserviceGraph.addVertex(carts)
        microserviceGraph.addVertex(user)
        microserviceGraph.addVertex(queuemaster)
        microserviceGraph.addEdge(queuemaster, shipping, MicroserviceEdge("simulates"))
        microserviceGraph.addEdge(shipping, orders, MicroserviceEdge("requires"))
        println("success!")
    }

    private fun createDotRepresentation(details : DetailLevel?): String {
        return GraphUtil.exportDotFromGraph(microserviceGraph, details)
    }

    // it would also be possible to use JGraphX for visualization. Might be worth checking?
    private fun createImageRepresentation(imageConfig: ImageConfig, path: File, details : DetailLevel?) {
        if (imageConfig.height == null && imageConfig.width == null)
            imageConfig.format?.let {
                Graphviz.fromString(createDotRepresentation(details))
                    .render(it).toFile(path)
            }
        if (imageConfig.height != null && imageConfig.width != null)
            imageConfig.format?.let {
                Graphviz.fromString(createDotRepresentation(details))
                    .height(imageConfig.height).width(imageConfig.width).render(it).toFile(path)
            }
        if (imageConfig.height != null && imageConfig.width == null)
            imageConfig.format?.let {
                Graphviz.fromString(createDotRepresentation(details)).height(imageConfig.height)
                    .render(it).toFile(path)
            }
        if (imageConfig.height == null && imageConfig.width != null)
            imageConfig.format?.let {
                Graphviz.fromString(createDotRepresentation(details)).width(imageConfig.width)
                    .render(it).toFile(path)
            }
    }
}

