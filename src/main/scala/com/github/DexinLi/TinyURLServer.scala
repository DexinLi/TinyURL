package com.github.DexinLi

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.StatusCodes.MovedPermanently
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.util.{Failure, Success}

class TinyURLServer(port: Int) {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  private val logger = LoggerFactory.getLogger(getClass)
  private val serverSource = Http().bind(interface = "0.0.0.0", port = port)
  private val mongoDao = new MongoDao
  private val redisDao = new RedisDao

  val response404 = HttpResponse(404, entity = "Unknown resource!")
  val homePage = HttpResponse(entity = HttpEntity(
    ContentTypes.`text/html(UTF-8)`,
    "<html><body>Welcome!</body></html>"))

  def responseSuccess(path: String) = HttpResponse(200, entity = path)

  def createRedirection(address: String): HttpResponse = {
    val locationHeader = Location(address)
    HttpResponse(MovedPermanently, headers = List(locationHeader))
  }

  val responseError = HttpResponse(500)

  def requestHandler(httpRequest: HttpRequest): Future[HttpResponse] = {
    val response = httpRequest match {
      case HttpRequest(GET, Path("/"), _, _, _) =>
        Future(homePage)
      case HttpRequest(GET, Path(_id), _, _, _) =>
        val id = _id.tail
        redisDao.getAddress(id).transformWith {
          case Success(None) =>
            mongoDao.getAddress(id).map {
              case Some(address) =>
                redisDao.put(id, address)
                redisDao.put(address, id)
                createRedirection(address)
              case None =>
                response404
            }
          case Success(Some(address)) =>
            Future(createRedirection(address))
          case Failure(e) =>
            logger.error("redis query for id error: " + e.getMessage)
            Future(responseError)
        }
      case HttpRequest(PUT, Path(_address), _, _, _) =>
        val address = _address.tail
        redisDao.getId(address).transformWith {
          case Success(Some(id)) =>
            Future(responseSuccess(id))
          case Success(None) =>
            mongoDao.getId(address).map {
              case Some(id) =>
                redisDao.put(id, address)
                redisDao.put(address, id)
                responseSuccess(id)
              case None =>
                val id = mongoDao.generateID(address)
                redisDao.put(id, address)
                redisDao.put(address, id)
                responseSuccess(id)
            }
          case Failure(e) =>
            logger.error("redis query for address error: " + e.getMessage)
            Future(responseError)
        }
      case r: HttpRequest =>
        r.discardEntityBytes() // important to drain incoming HTTP Entity stream
        Future(response404)
    }
    response
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
