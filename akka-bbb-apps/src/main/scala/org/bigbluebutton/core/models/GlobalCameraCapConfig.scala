package org.bigbluebutton.core.models

import com.typesafe.config.Config

import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

/**
 * A load-driven camera ceiling. The entry applies when either measurement is at
 * or above its own threshold; among the applicable entries the lowest `max` wins.
 */
case class CameraCapLoadThreshold(cpu: Double, memory: Double, max: Int)

case class GlobalCameraCapConfig(
    enabled:             Boolean,
    max:                 Int,
    minPerMeeting:       Int,
    evaluationInterval:  Long,
    releaseDelay:        Long,
    loadSmoothingFactor: Double,
    allowCreateOverride: Boolean,
    loadThresholds:      Vector[CameraCapLoadThreshold]
)

object GlobalCameraCapConfig {
  val Disabled: GlobalCameraCapConfig = GlobalCameraCapConfig(
    enabled = false,
    max = 0,
    minPerMeeting = 1,
    evaluationInterval = 5000L,
    releaseDelay = 30000L,
    loadSmoothingFactor = 0.3d,
    allowCreateOverride = false,
    loadThresholds = Vector.empty
  )

  /**
   * Clamped low: a zero or negative interval would spin the evaluation loop, and
   * every pass re-reads /proc/meminfo and re-allocates every meeting on the server.
   */
  private def millis(config: Config, path: String, default: Long, minimum: Long): Long =
    Try(config.getDuration(path, TimeUnit.MILLISECONDS).longValue())
      .map(math.max(minimum, _))
      .getOrElse(default)

  /**
   * An entry whose `max` is missing or unparseable is dropped rather than read as
   * zero: a typo would otherwise mean "stop every camera on the server" the first
   * time the threshold is crossed, applied immediately because tightening is never
   * delayed.
   */
  private def thresholds(config: Config): Vector[CameraCapLoadThreshold] = Try {
    config.getConfigList("globalCameraCap.loadThresholds").asScala.toVector.flatMap { entry =>
      Try(entry.getInt("max")).toOption.filter(_ > 0).map { max =>
        CameraCapLoadThreshold(
          cpu = Try(entry.getDouble("cpu")).getOrElse(Double.MaxValue),
          memory = Try(entry.getDouble("memory")).getOrElse(Double.MaxValue),
          max = max
        )
      }
    }
  }.getOrElse(Vector.empty)

  def fromConfig(config: Config): GlobalCameraCapConfig = GlobalCameraCapConfig(
    enabled = Try(config.getBoolean("globalCameraCap.enabled")).getOrElse(Disabled.enabled),
    max = math.max(0, Try(config.getInt("globalCameraCap.max")).getOrElse(Disabled.max)),
    minPerMeeting = math.max(0, Try(config.getInt("globalCameraCap.minPerMeeting")).getOrElse(Disabled.minPerMeeting)),
    evaluationInterval = millis(config, "globalCameraCap.evaluationInterval", Disabled.evaluationInterval, 1000L),
    releaseDelay = millis(config, "globalCameraCap.releaseDelay", Disabled.releaseDelay, 0L),
    // 1.0 disables smoothing (each sample replaces the average outright).
    loadSmoothingFactor = Try(config.getDouble("globalCameraCap.loadSmoothingFactor"))
      .map(f => math.min(1.0d, math.max(0.01d, f)))
      .getOrElse(Disabled.loadSmoothingFactor),
    allowCreateOverride = Try(config.getBoolean("globalCameraCap.allowCreateOverride"))
      .getOrElse(Disabled.allowCreateOverride),
    loadThresholds = thresholds(config)
  )
}
