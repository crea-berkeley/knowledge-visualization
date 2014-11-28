package crea.www

import scala.util.matching.Regex
import scala.util.{Try, Success, Failure}

import scala.concurrent._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.scalajs._
import scala.scalajs.js.Dynamic.newInstance
import js.annotation.JSExport

import org.scalajs.dom
import dom.document
import dom.{WebSocket, MessageEvent, console}

object WebApp extends js.JSApp {

  private[this] val client = { 

    new WebSocket("ws://high-fructose-corn-syrup.uwaterloo.ca:8080/")

  } 

  private[this] def autocomplete(input : String) : Regex = { 
    
    input.toList.mkString("""[\s\w]*""").concat("""[\s\w]*""").r

  }

  private[this] def sigmajs = js.Dynamic.global.sigma

  private[this] lazy val sigma = {

    val settings = js.Dynamic.literal(
      font = "serif",
      drawEdgeLabels = true,
      edgeLabelSize = "fixed",
      defaultEdgeLabelSize = 12,
      defaultEdgeLabelColor = "#997F46",
      edgeLabelThreshold = 1.0,
      labelSize = "proportional",
      labelThreshold = 2.5,
      labelSizeRatio = 3.0,
      defaultLabelSize = 16,
      defaultLabelColor = "#FFD67D",
      minNodeSize = 0.25,
      maxNodeSize = 5,
      minEdgeSize = 0.1,
      maxEdgeSize = 0.4,
      scalingMode = "inside",
      hideEdgesOnMove = true,
      doubleClickEnabled = false,
      zoomMin = 0.005
    )

    val config = js.Dynamic.literal(
      container = "graph-container",
      graph = newInstance(sigmajs.classes.graph)(),
      settings = settings
    )

    newInstance(sigmajs)(config)

  }

  private[this] def dictionary : Array[String] = {
    sigma.graph.nodes().asInstanceOf[js.Array[js.Dynamic]].map((_ : js.Dynamic).label.asInstanceOf[String])
  }

  private[this] def searchInput : Option[String] = {
    Option(document.getElementById("search-input"))
      .map(_.asInstanceOf[js.Dynamic]
      .value
      .asInstanceOf[String])
  }

  @JSExport
  def search() = { 
    
    searchInput.foreach(s => client.send(js.Any.fromString(s)))

  } 

  @JSExport
  def showSuggestions() = Option(document.getElementById("suggestions")).map { suggestions =>

    while(suggestions.hasChildNodes()) {
      suggestions.removeChild(suggestions.lastChild)
    }

    searchInput.map { label =>

      val dict = dictionary

      if(dict.size <= 500) {

        val items = dict.filter(suggestion => autocomplete(label).pattern.matcher(suggestion).matches)
          .sortBy(Levenshtein(label))
          .take(20)

        if(items.length > 0) {

          items.foreach { suggestion =>

            val li = document.createElement("li")

            li.onclick = (e : dom.MouseEvent) => client.send(suggestion)

            li.appendChild(document.createTextNode(suggestion))

            suggestions.appendChild(li)

          }

          suggestions.style.display = "block";

        } else {

          hideSuggestions()

        }

      }

    }

  }

  @JSExport
  def hideSuggestions() = {

    document.getElementById("suggestions").style.display = "none"

  }

  def main() = {

    val atlasConfig = js.Dynamic.literal(adjustSizes=true)

    client.onmessage = { (event : MessageEvent) =>

      val data = js.JSON.parse(event.data.asInstanceOf[String]).asInstanceOf[js.Dynamic]

      console.log(data)

      val subject = data.subject
      val obj = data.obj

      val subjectNode = js.Dynamic.literal(
        id = subject,
        size = 0.5,
        x = Math.random() * 10,
        y = Math.random() * 10,
        `type` = subject,
        label = subject
      )

      val objectNode = js.Dynamic.literal(
        id = obj,
        size = 0.5,
        x = Math.random() * 10,
        y = Math.random() * 10,
        `type` = obj,
        label = obj
      )

      val edge = js.Dynamic.literal(
        label = data.predicate,
        id = data.timestamp,
        source = subject,
        target = obj,
        pmid = data.pmid,
        elapsed = data.elapsed,
        timestamp = data.timestamp
      )

      sigma.stopForceAtlas2()

      scala.util.Try(sigma.graph.addNode(subjectNode))
      scala.util.Try(sigma.graph.addNode(objectNode))
      sigma.graph.addEdge(edge)

      val graph = js.Dynamic.literal(
        nodes=sigma.graph.nodes(),
        edges=sigma.graph.edges()
      )

      dom.localStorage.setItem("graph", js.JSON.stringify(graph))

      sigma.refresh()

      sigma.startForceAtlas2(atlasConfig)

    }

    sigmajs.renderers.`def` = sigmajs.renderers.canvas

    sigmajs.plugins.dragNodes(sigma, sigma.renderers.asInstanceOf[js.Array[js.Dynamic]](0))

    Option(dom.localStorage.getItem("graph"))
      .map(v => js.JSON.parse(v.asInstanceOf[String]))
      .foreach(graph => { 

        sigma.stopForceAtlas2()
        sigma.graph.clear()
        sigma.graph.read(graph)
        sigma.refresh()
        sigma.startForceAtlas2(atlasConfig)

      })
        

  }

}
