package de.fhdo.lemma.model_processing.visualizer.graphviz

import de.fhdo.lemma.model_processing.visualizer.model.DetailLevel
import de.fhdo.lemma.model_processing.visualizer.model.MicroserviceEdge
import de.fhdo.lemma.model_processing.visualizer.model.MicroserviceVertex
import de.fhdo.lemma.model_processing.visualizer.model.OperationSubVertex
import org.jgrapht.Graph
import org.jgrapht.nio.Attribute
import org.jgrapht.nio.AttributeType
import org.jgrapht.nio.DefaultAttribute
import java.io.StringWriter
import org.jgrapht.nio.dot.DOTExporter

/**
 * Static Utility class which configures the DotExporter of JGraphT through functions.
 *
 * @author [Jonas Sorgalla](mailto:jonas.sorgalla@fh-dortmund.de)
 */
class GraphUtil {
    companion object {
        // Sadly the chosen font name is platform dependent
        // See https://graphviz.org/doc/info/attrs.html#d:fontname
        // On UNIX and Mac systems the default should be Quartz
        private const val FONT: String = "Helvetica"

        var details = DetailLevel.SERVICES

        fun exportDotFromGraph(serviceGraph: Graph<MicroserviceVertex, MicroserviceEdge>, details : DetailLevel?): String {
            if (details != null) {
                GraphUtil.details = details
            }
            val exporter: DOTExporter<MicroserviceVertex, MicroserviceEdge> = DOTExporter(
                //Lambda which functions as id provider for the vertexes
                {v -> "\"${v.qualifiedName}\""}
            )
            //Lambda for decorating the vertices with attributes
            val vertexAttributeProvider : (MicroserviceVertex) -> MutableMap<String, Attribute> = {
                val attributes = mutableMapOf<String, Attribute>()
                attributes["label"] = DefaultAttribute(htmlLabel(it), AttributeType.HTML)
                attributes["type"] = DefaultAttribute.createAttribute(it.type)
                attributes["shape"] = DefaultAttribute.createAttribute("plaintext")
                val color = when(it.type) {
                    "FUNCTIONAL" -> "black"
                    "INFRASTRUCTURE" -> "black"
                    "UTILITY" -> "black"
                    else -> "red"
                }
                attributes["color"] = DefaultAttribute.createAttribute(color)
                //attributes.put("fontsize", DefaultAttribute.createAttribute(12))
                attributes["fontname"] = DefaultAttribute.createAttribute(FONT)
                //last part of a lambda is always considered its return value
                attributes }

            //Lambda for decorating the edges with attributes
            val edgeAttributeProvider : (MicroserviceEdge) -> MutableMap<String, Attribute> = {
                val attributes = mutableMapOf<String, Attribute>()
                attributes["label"] = DefaultAttribute.createAttribute(it.label)
                attributes["fontname"] = DefaultAttribute.createAttribute(FONT)
                //last part of a lambda is always considered its return value
                attributes }

            //Lambda for decorating the graph with attributes
            val graphAttributeProvider : () -> MutableMap<String, Attribute> = {
                val attributes = mutableMapOf<String, Attribute>()
                //attributes["rankdir"] = DefaultAttribute.createAttribute("LR")
                //last part of a lambda is always considered its return value
                attributes }

            // Setting the previously defined functions as new providers for the exporter
            exporter.setVertexAttributeProvider(vertexAttributeProvider)
            exporter.setEdgeAttributeProvider(edgeAttributeProvider)
            exporter.setGraphAttributeProvider(graphAttributeProvider)
            val writer = StringWriter()
            exporter.exportGraph(serviceGraph, writer)
            return writer.toString()
        }

        private fun htmlLabel(it: MicroserviceVertex): String {
            val bgcolor = when(it.type) {
                "FUNCTIONAL" -> "#87cefa"
                "INFRASTRUCTURE" -> "#c1005d"
                "UTILITY" -> "#80c100"
                else -> "#d3d3d3"
            }
            //HTML template to display the service vertices
            return "<table bgcolor='${bgcolor}' border='1' cellborder='0'>" +
                    "<tr><td><i>&laquo;${it.type.toString().toLowerCase().capitalize()} Service&raquo;</i></td></tr>" +
                    "<tr><td>${it.visibility.visibilityHtml()} <b>${it.qualifiedName.substring(it.qualifiedName.lastIndexOf('.') + 1)}</b></td></tr>" +
                    (if (it.technology != null) "<tr><td>service technologies</td></tr>" else "") +
                    (if (it.technology != null) "<tr><td>{${it.technology}}</td></tr>" else "") +
                    (if (details.ordinal >= DetailLevel.INTERFACES.ordinal) it.interfacesHtml() else "") +
            "</table>"
        }

        private fun MicroserviceVertex.interfacesHtml(): String {
            val lines : MutableList<String> = mutableListOf()
            this.interfaces.forEach { isv ->
                lines.add("<tr><td><table bgcolor='white' cellspacing='0'><tr><td>&laquo;Interface&raquo;<br/>${isv.visibility.visibilityHtml() + isv.name}</td></tr>")
                if ((details.ordinal >= DetailLevel.OPERATIONS.ordinal)) {
                    lines.add("<tr><td>")
                    isv.operations.forEach {osv ->
                        lines.add(
                            "${osv.visibility.visibilityHtml() + osv.name}(${
                                if (details.ordinal >= DetailLevel.SIGNATURES.ordinal) osv.paramHtml() else "..."
                            })<br/>"
                        )
                    }
                    lines.add("</td></tr>")
                }
                lines.add("</table></td></tr>")
            }
            return lines.joinToString(separator="")
        }

        private fun OperationSubVertex.paramHtml(): String {
            return this.parameters.joinToString(separator=",") { "${it.commType} ${it.name} : ${it.datatype}"}
        }

        private fun String.visibilityHtml(): String {
            return when(this) {
                "PUBLIC" -> "+"
                "INTERNAL" -> "-"
                "IN_MODEL" -> "#"
                "ARCHITECTURE" -> "#"
                else -> ""
            }
        }
    }
}

