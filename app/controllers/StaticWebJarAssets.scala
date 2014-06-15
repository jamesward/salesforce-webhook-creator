package controllers

import play.api.mvc.Controller
import play.api.Play.current


object StaticWebJarAssets extends Controller {

  // prepends a url if the assets.url config is set
  def url(file: String): String = {
    val baseUrl = routes.WebJarAssets.at(file).url
    current.configuration.getString("assets.url").fold(baseUrl)(_ + baseUrl)
  }

}