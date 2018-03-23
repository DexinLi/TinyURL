package com.github.DexinLi

import scala.concurrent.Future

trait Dao {

  def getId(address: String): Future[Option[String]]

  def getAddress(id: String): Future[Option[String]]

  def put(id: String, address: String): Future[Unit]

}
