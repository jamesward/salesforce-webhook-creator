package utils

import java.util.Collection
import java.util.AbstractMap
import java.util.Map
import java.lang.Boolean

import scala.collection.JavaConverters._

import com.fasterxml.jackson.databind.node.ObjectNode
import org.webjars.RequireJS

object RequireJSUtil {

  def requireJsConfigs(prefix: String): Collection[ObjectNode] = {
    val entry: Map.Entry[String, Boolean] = new AbstractMap.SimpleEntry(prefix, Boolean.FALSE)
    val prefixes:List[Map.Entry[String, Boolean]] = List(entry)
    RequireJS.generateSetupJson(prefixes.asJava).values()
  }

}
