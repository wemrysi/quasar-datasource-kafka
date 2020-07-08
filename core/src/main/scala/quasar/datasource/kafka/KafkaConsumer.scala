/*
 * Copyright 2020 Precog Data
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quasar.datasource.kafka

import slamdata.Predef._

import org.apache.kafka.common.TopicPartition

import cats.Applicative
import cats.effect._
import cats.implicits._
import fs2.kafka.{CommittableConsumerRecord, ConsumerSettings, consumerResource}
import fs2.{Stream, kafka}

class KafkaConsumer[F[_]: Applicative: ConcurrentEffect: ContextShift: Timer, K, V](
    settings: ConsumerSettings[F, K, V],
    decoder: RecordDecoder[F, K, V])
    extends Consumer[F] {

  override def fetch(topic: String): Resource[F, Stream[F, Byte]] = {
    consumerResource[F]
      .using(settings)
      .evalTap(_.subscribeTo(topic))

      .evalMap(getOffsets)
      .map {
        case (consumer, offsets) =>
          consumer.partitionedStream
            .map(_.takeThrough(isOffsetLimit(_, offsets)))
            .parJoinUnbounded
            .flatMap(decoder)
      }
  }

  def getOffsets(consumer: kafka.KafkaConsumer[F, K, V]): F[(fs2.kafka.KafkaConsumer[F, K, V], Map[TopicPartition, Long])] = {
    consumer.assignment.flatMap(consumer.endOffsets).map(consumer -> _)
  }

  def isOffsetLimit(committableRecord: CommittableConsumerRecord[F, K, V], offsets: Map[TopicPartition, Long]): Boolean = {
    val record = committableRecord.record
    val topic = record.topic
    val partition = record.partition
    val topicPartition = new TopicPartition(topic, partition)
    val end = offsets(topicPartition)
    record.offset >= end
  }
}

object KafkaConsumer {

  def apply[F[_]: ConcurrentEffect: ContextShift: Timer, K, V](
      settings: ConsumerSettings[F, K, V],
      decoder: RecordDecoder[F, K, V])
      : Consumer[F] =
    new KafkaConsumer(settings, decoder)
}
