package com.github.DexinLi

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scredis.Redis

import scala.concurrent.Future

class RedisDao extends Dao {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  private val redisClient = Redis()

  def put(key: String, value: String): Future[Boolean] = {
    redisClient.set(key, value)
  }

  def getId(address: String): Future[Option[String]] = {
    redisClient.get(address)
  }

  def getAddress(id: String): Future[Option[String]] = {
    redisClient.get(id)
  }

}
