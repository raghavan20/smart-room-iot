package org.ragdan

import java.net._
import java.util.concurrent._

import com.codahale.metrics.SlidingWindowReservoir

object Smart extends App {

  val m = new SlidingWindowReservoir(5)
  val port = 5006

  val MOTION_DETECTED = 1
  val MOTION_NOT_DETECTED = 0

  val LON07_ALTO = "CONF_46608@cisco.com"
  val LON07_BOARDROOM = "CONF_37445@cisco.com"

  val exchange = createExchange

  // initialize with motion not detected
  (1 to 5).foreach { x =>
    m.update(MOTION_NOT_DETECTED)
  }


  val udpListener = new Thread(new Runnable() {
    def run() {

      val udpServerSocket = new DatagramSocket(port)

      val buf = new Array[Byte](1000)
      val udpPacket = new DatagramPacket(buf, buf.length)

      while (true) {
        udpServerSocket.receive(udpPacket)
        val data = udpPacket.getData
        val s = new String(data, 0, udpPacket.getLength)

        if (s == "motion detected") {
          m.update(MOTION_DETECTED)
        } else {
          m.update(MOTION_NOT_DETECTED)
        }
      }
    }
  })
  udpListener.start()


  val controlLightRunnable = new Runnable() {
    def run() = {
      val isRoomOccupied = m.getSnapshot.getValues.contains(MOTION_DETECTED)
      val roomStatus = exchange.isRoomAvailable(LON07_ALTO)

      if (!isRoomOccupied && roomStatus == "free") {
        println("trigger green light on smart bulb")
        HueApi.updateLight(state = State.green)
      } else if (isRoomOccupied && roomStatus == "free") {
        println("trigger amber light on smart bulb")
        HueApi.updateLight(state = State.yellow)
      } else if (!isRoomOccupied && roomStatus != "free") {
        println("trigger amber light on smart bulb")
        HueApi.updateLight(state = State.yellow)
      } else {
        println("trigger red light on smart bulb")
        HueApi.updateLight(state = State.red)
      }
    }
  }

  val executor = Executors.newSingleThreadScheduledExecutor()
  executor.scheduleAtFixedRate(controlLightRunnable, 5, 5, TimeUnit.SECONDS)

  def createExchange:Exchange = {
    print("enter user: ")
    val user = new String(System.console().readLine())

    print("enter password: ")
    val password = new String(System.console().readPassword())

    new Exchange(user, password)
  }

}
