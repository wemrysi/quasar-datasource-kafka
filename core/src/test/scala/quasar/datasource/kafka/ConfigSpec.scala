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

import org.specs2.mutable.Specification

import argonaut.Argonaut._
import argonaut._
import cats.data.NonEmptyList
import quasar.connector.DataFormat

class ConfigSpec extends Specification {

  def missField(field: String) = beLeft(beRight(beLike[(String, CursorHistory)] {
    case (_, CursorHistory(List(El(CursorOpDownField(`field`), false)))) => ok
    case (_, CursorHistory(List(El(CursorOpDownField(`field`), true)))) => ko(s"$field was found")
    case (_, CursorHistory(List(El(CursorOpDownField(other), _)))) => ko(s"parse error on $other instead of $field")
  }))

  def haveEmptyList(field: String) = beLeft(beRight(beLike[(String, CursorHistory)] {
    case (msg, _) => msg mustEqual s"$field value cannot be an empty array"
  }))

  "json decoder" >> {
    "succeeds on valid configuration" >> {
      val s =
        """
          |{
          | "bootstrapServers": [ "a.b.c.d:xyzzy", "d.e.f.g:yzzyx" ],
          | "groupId": "precog",
          | "topics": [ "a", "b", "c" ],
          | "decoder": "RawKey",
          | "format": {
          |   "type": "json",
          |   "variant": "line-delimited",
          |   "precise": false
          | }
          |}""".stripMargin

      s.decode[Config] must beRight(
        Config(
          bootstrapServers = NonEmptyList.of("a.b.c.d:xyzzy","d.e.f.g:yzzyx"),
          groupId = "precog",
          topics = NonEmptyList.of("a", "b", "c"),
          decoder = Decoder.rawKey,
          format = DataFormat.ldjson))
    }

    "parses compressed formats" >> {
      val s =
        """
          |{
          | "bootstrapServers": [ "a.b.c.d:xyzzy", "d.e.f.g:yzzyx" ],
          | "groupId": "precog",
          | "topics": [ "a", "b", "c" ],
          | "decoder": "RawKey",
          | "format": {
          |   "type": "json",
          |   "variant": "line-delimited",
          |   "precise": false
          | },
          | "compressionScheme": "gzip"
          |}""".stripMargin

      s.decode[Config] must beRight(
        Config(
          bootstrapServers = NonEmptyList.of("a.b.c.d:xyzzy","d.e.f.g:yzzyx"),
          groupId = "precog",
          topics = NonEmptyList.of("a", "b", "c"),
          decoder = Decoder.rawKey,
          format = DataFormat.gzipped(DataFormat.ldjson)))
    }

    "fails on missing bootstrapServers" >> {
      val s =
        """
          |{
          | "groupId": "precog",
          | "topics": [ "a", "b", "c" ],
          | "decoder": "RawValue",
          | "format": {
          |   "type": "json",
          |   "variant": "line-delimited",
          |   "precise": false
          | }
          |}""".stripMargin

      s.decode[Config] must missField("bootstrapServers")
    }

    "fails on empty bootstrapsServers" >> {
      val s =
        """
          |{
          | "bootstrapServers": [],
          | "groupId": "precog",
          | "topics": [ "a", "b", "c" ],
          | "decoder": "RawKey",
          | "format": {
          |   "type": "json",
          |   "variant": "line-delimited",
          |   "precise": false
          | }
          |}""".stripMargin

      s.decode[Config] must haveEmptyList("bootstrapServers")
    }

    "fails on missing groupId" >> {
      val s =
        """
          |{
          | "bootstrapServers": [ "a.b.c.d:xyzzy", "d.e.f.g:yzzyx" ],
          | "topics": [ "a", "b", "c" ],
          | "decoder": "RawValue",
          | "format": {
          |   "type": "json",
          |   "variant": "line-delimited",
          |   "precise": false
          | }
          |}""".stripMargin

      s.decode[Config] must missField("groupId")
    }

    "fails on missing topics" >> {
      val s =
        """
          |{
          | "bootstrapServers": [ "a.b.c.d:xyzzy", "d.e.f.g:yzzyx" ],
          | "groupId": "precog",
          | "decoder": "RawKey",
          | "format": {
          |   "type": "json",
          |   "variant": "line-delimited",
          |   "precise": false
          | }
          |}""".stripMargin

      s.decode[Config] must missField("topics")
    }

    "fails on empty topics" >> {
      val s =
        """
          |{
          | "bootstrapServers": [ "a.b.c.d:xyzzy", "d.e.f.g:yzzyx" ],
          | "groupId": "precog",
          | "topics": [],
          | "decoder": "RawValue",
          | "format": {
          |   "type": "json",
          |   "variant": "line-delimited",
          |   "precise": false
          | }
          |}""".stripMargin

      s.decode[Config] must haveEmptyList("topics")
    }

    "fails on missing decoder" >> {
      val s =
        """
          |{
          | "bootstrapServers": [ "a.b.c.d:xyzzy", "d.e.f.g:yzzyx" ],
          | "groupId": "precog",
          | "topics": [ "a", "b", "c" ],
          | "format": {
          |   "type": "json",
          |   "variant": "line-delimited",
          |   "precise": false
          | }
          |}""".stripMargin

      s.decode[Config] must missField("decoder")
    }

    "fails on missing format" >> {
      val s =
        """
          |{
          | "bootstrapServers": [ "a.b.c.d:xyzzy", "d.e.f.g:yzzyx" ],
          | "groupId": "precog",
          | "topics": [ "a", "b", "c" ],
          | "decoder": "RawKey"
          |}""".stripMargin

      s.decode[Config] must missField("format")
    }
  }

  "sanitize" >> {
    "strips sensitive data" >> {
      skipped
    }

    "is identity on non-sensitive data" >> {
      val c = Config(
        bootstrapServers = NonEmptyList.of("a.b.c.d:xyzzy","d.e.f.g:yzzyx"),
        groupId = "precog",
        topics = NonEmptyList.of("a", "b", "c"),
        decoder = Decoder.rawValue,
        format = DataFormat.json)

      c.sanitize mustEqual c
    }
  }

  "reconfigure" >> {
    "replaces non-sensitive data as right" >> {
      val c = Config(
        bootstrapServers = NonEmptyList.of("a.b.c.d:xyzzy","d.e.f.g:yzzyx"),
        groupId = "precog",
        topics = NonEmptyList.of("a", "b", "c"),
        decoder = Decoder.rawValue,
        format = DataFormat.json)

      val d = Config(
        bootstrapServers = NonEmptyList.of("w.x.y.z:abcd"),
        groupId = "precog2",
        topics = NonEmptyList.of("topic"),
        decoder = Decoder.rawValue,
        format = DataFormat.json)

      c.reconfigure(d) must beRight(d)
    }

    "sanitizes patch with sensitive data as left" >> {
      skipped
    }
  }
}
