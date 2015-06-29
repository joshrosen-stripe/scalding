package com.twitter.scalding.reducer_estimation

import scala.collection.JavaConverters._
import cascading.flow.FlowStep
import cascading.tap.{ Tap, CompositeTap }
import cascading.tap.hadoop.Hfs
import org.apache.hadoop.mapred.JobConf
import org.slf4j.LoggerFactory

object InputSizeReducerEstimator {
  val BytesPerReducer = "scalding.reducer.estimator.bytes.per.reducer"
  val defaultBytesPerReducer = 1L << 33 // 8 GB

  /** Get the target bytes/reducer from the JobConf */
  def getBytesPerReducer(conf: JobConf): Long = conf.getLong(BytesPerReducer, defaultBytesPerReducer)
}

/**
 * Estimator that uses the input size and a fixed "bytesPerReducer" target.
 *
 * Bytes per reducer can be configured with configuration parameter, defaults to 1 GB.
 */
class InputSizeReducerEstimator extends ReducerEstimator {

  private val LOG = LoggerFactory.getLogger(this.getClass)

  /**
   * Figure out the total size of the input to the current step and set the number
   * of reducers using the "bytesPerReducer" configuration parameter.
   */
  override def estimateReducers(info: FlowStrategyInfo): Option[Int] =
    Common.inputSizes(info.step) match {
      case inputSizes if inputSizes.isEmpty =>
        LOG.warn("InputSizeReducerEstimator unable to estimate reducers; " +
          "cannot compute size of:\n - " +
          Common.unrollTaps(info.step).filterNot(_.isInstanceOf[Hfs]).mkString("\n - "))
        None
      case inputSizes =>
        val bytesPerReducer =
          InputSizeReducerEstimator.getBytesPerReducer(info.step.getConfig)

        val totalBytes = inputSizes.map(_._2).sum
        val nReducers = (totalBytes.toDouble / bytesPerReducer).ceil.toInt max 1

        lazy val logStr = inputSizes.map {
          case (name, bytes) => "   - %s\t%d\n".format(name, bytes)
        }.mkString("")

        LOG.info("\nInputSizeReducerEstimator" +
          "\n - input size (bytes): " + totalBytes +
          "\n - reducer estimate:   " + nReducers +
          "\n - Breakdown:\n" +
          logStr)

        Some(nReducers)
    }
}
