package com.github.DexinLi

import com.mongodb.casbah.MongoClient
import com.mongodb.casbah.commons.MongoDBObject

import scala.concurrent.Future
import scala.util.Random

class MongoDao extends Dao {
  private val mongoClient = MongoClient()
  //MongoClient("db")
  private val tinyURLdb = mongoClient("TinyURL")
  private val URLCollection = tinyURLdb("URL")
  URLCollection.createIndex(
    MongoDBObject(("ID", 1), ("address", 1)),
    MongoDBObject(("background", 1))
  )
  private val numCollection = tinyURLdb("num")
  numCollection.createIndex(MongoDBObject(("num", 1)), MongoDBObject(("background", 1)))
  private val random = Random
  random.setSeed(System.nanoTime())

  override def generateID(address: String): String = {
    var valid = true
    var num: Long = 0
    do {
      //Atomic, don't worry
      num = random.nextLong().abs
      val res = numCollection.find(MongoDBObject(("num", num)))
      valid = res.isEmpty
    } while (!valid)
    numCollection.insert(MongoDBObject(("num", num)))
    val stringBuilder = new StringBuilder()
    while (num > 0) {
      val t = num % 64
      num = num / 64
      if (t < 10) {
        stringBuilder += (t + '0').toChar
      } else if (t < 10 + 26) {
        stringBuilder += (t - 10 + 'A').toChar
      } else if (t == 62) {
        stringBuilder += '-'
      } else if (t == 63) {
        stringBuilder += '_'
      } else {
        stringBuilder += (t - 36 + 'a').toChar
      }
    }
    stringBuilder.result()
  }
  override def getId(address: String): Future[Option[String]] = Future {
    URLCollection.findOne(MongoDBObject(("address", address))).map(_.get("ID").toString)
  }

  override def getAddress(id: String): Future[Option[String]] = Future {
    URLCollection.findOne(MongoDBObject(("ID", id))).map(_.get("address").toString)
  }

  override def put(id: String, address: String): Future[Unit] = Future {
    URLCollection.insert(MongoDBObject(("ID", id), ("address", address)))
  }
}
