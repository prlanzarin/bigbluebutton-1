package org.bigbluebutton.core.apps.webcam

import java.lang.management.ManagementFactory
import scala.io.Source
import scala.util.Try

/**
 * Host-wide CPU and memory utilisation.
 *
 * BigBlueButton is a monolith: mediasoup, LiveKit and FreeSWITCH share this box,
 * so the host figures already account for the media load this cap exists to
 * contain. A split or containerised deployment would read the wrong machine -
 * which is why the load axis is opt-in.
 */
object ServerLoadSampler {

  private lazy val osBean = ManagementFactory.getOperatingSystemMXBean

  private lazy val sunOsBean: Option[com.sun.management.OperatingSystemMXBean] =
    Try(osBean.asInstanceOf[com.sun.management.OperatingSystemMXBean]).toOption

  private val MemInfoPath = "/proc/meminfo"

  def sample(): Option[ServerLoad] = for {
    cpu <- cpuUtilisation()
    memory <- memoryUtilisation()
  } yield ServerLoad(cpu, memory)

  /**
   * No reading rather than a substitute one. getCpuLoad has nothing to report on
   * the first call, and the load average is a different metric on a different
   * scale - seeding the smoothed average with it would peg a freshly restarted
   * server at its tightest ceiling, applied immediately.
   */
  private def cpuUtilisation(): Option[Double] =
    sunOsBean.map(_.getCpuLoad).filter(v => !v.isNaN && v >= 0)

  /**
   * MemAvailable, with no fallback. On Linux most "used" memory is reclaimable
   * page cache, so the MXBean's free/total reports a nearly full machine on an
   * idle server - falling back to it would trip every threshold rather than
   * report nothing, and reporting nothing leaves the last known load in place.
   */
  private def memoryUtilisation(): Option[Double] = memInfoUtilisation()

  private def memInfoUtilisation(): Option[Double] = Try {
    val source = Source.fromFile(MemInfoPath)
    val fields = try {
      source
        .getLines()
        .map(_.split(":", 2))
        .collect { case Array(key, value) => key.trim -> value.trim.split("\\s+").head.toLong }
        .toMap
    } finally source.close()

    for {
      total <- fields.get("MemTotal") if total > 0
      available <- fields.get("MemAvailable")
    } yield math.min(1.0d, math.max(0.0d, 1.0d - (available.toDouble / total.toDouble)))
  }.toOption.flatten
}
