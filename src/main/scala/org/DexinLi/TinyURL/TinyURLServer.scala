package org.DexinLi.TinyURL

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.StatusCodes.MovedPermanently
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import com.mongodb.casbah.Imports._
import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.MongoDBObject
import com.redis.RedisClient
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.Random

object TinyURLServer extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  val logger = LoggerFactory.getLogger(getClass)
  val serverSource = Http().bind(interface = "0.0.0.0", port = 9000)
  //TODO use replicas, create a specific redis server to pull the data
  val redisClient = new RedisClient("redis", 6379)
  //TODO use replicas, add authentication
  val mongoClient = MongoClient("db")
  val tinyURLdb = mongoClient("TinyURL")
  val URLCollection = tinyURLdb("URL")
  if (URLCollection.indexInfo.isEmpty) {
    URLCollection.createIndex(
      MongoDBObject(("ID", 1), ("address", 1)),
      MongoDBObject(("background", true))
    )
  }
  val numCollection = tinyURLdb("num")
  val response404 = HttpResponse(404, entity = "Unknown resource!")

  def responseSuccess(path: String) = HttpResponse(200, entity = path)

  def createRedirection(address: String): HttpResponse = {
    val locationHeader = Location(address)
    HttpResponse(MovedPermanently, headers = List(locationHeader))
  }

  val random = Random
  random.setSeed(System.currentTimeMillis())

  def generateID(address: String): String = {
    var valid = true
    var num: Long = 0
    do {
      //Atomic, don't worry
      num = random.nextLong()
      val res = numCollection.find(MongoDBObject(("num", num)))
      valid = res.isEmpty
    } while (valid)
    numCollection.insert(MongoDBObject(("num", num)))
    val stringBuilder = new StringBuilder()
    while (num > 0) {
      val t = num % 62
      num = num / 62
      if (t < 10) {
        stringBuilder += (t + '0').toChar
      } else if (t < 10 + 26) {
        stringBuilder += (t + 'A').toChar
      } else {
        stringBuilder += (t + 'a').toChar
      }
    }
    stringBuilder.result()
  }

  def requestHandler(httpRequest: HttpRequest): Future[HttpResponse] = {
    Future {
      httpRequest match {
        case HttpRequest(GET, uri, _, _, _) =>
          uri.path match {
            case Uri.Path("/") =>
              HttpResponse(entity = HttpEntity(
                ContentTypes.`text/html(UTF-8)`,
                "<html><body>Welcome!</body></html>"))
            case Uri.Path(id) =>
              redisClient.get(id) match {
                case None =>
                  val res = URLCollection.findOne(MongoDBObject(("ID", id)))
                  if (res.isEmpty) {
                    response404
                  } else {
                    if (res.size != 1) {
                      logger.error(s"duplicated id: $id\nin following objects: \n\t${res.mkString("\n\t")}\n")
                    }
                    val address = res.get("address").toString
                    createRedirection(address)
                  }
                case Some(address) => createRedirection(address)
              }
          }
        case HttpRequest(POST, uri, _, _, _) => uri.path match {
          case Uri.Path(address) =>
            val res = redisClient.get(address)
            if (res.isEmpty) {
              val res = URLCollection.findOne(MongoDBObject(("address", address)))
              if (res.isEmpty) {
                val id = generateID(address)
                URLCollection.insert(MongoDBObject(("ID", id), ("address", address)))
                redisClient.set(id, address)
                responseSuccess(id)
              } else {
                val id = res.get("ID").toString
                responseSuccess(id)
              }
            } else {
              val id = res.get
              responseSuccess(id)
            }
        }
        case r: HttpRequest =>
          r.discardEntityBytes() // important to drain incoming HTTP Entity stream
          response404
      }
    }
  }

  val bindingFuture: Future[Http.ServerBinding] =
    serverSource.to(Sink.foreach { connection =>
      val address = connection.remoteAddress
      logger.info("Accepted new connection from " + address)
      connection handleWithAsyncHandler requestHandler
    }).run()

  def end(): Unit = bindingFuture.flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate())
}
