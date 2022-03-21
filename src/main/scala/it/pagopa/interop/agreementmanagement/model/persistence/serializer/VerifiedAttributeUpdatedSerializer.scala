package it.pagopa.interop.agreementmanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.interop.agreementmanagement.model.persistence.VerifiedAttributeUpdated
import it.pagopa.interop.agreementmanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class VerifiedAttributeUpdatedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 100001

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val VerifiedAttributeUpdatedManifest: String = classOf[VerifiedAttributeUpdated].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: VerifiedAttributeUpdated =>
      serialize(event, VerifiedAttributeUpdatedManifest, currentVersion)
  }

  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case VerifiedAttributeUpdatedManifest :: `version1` :: Nil =>
      deserialize(v1.events.VerifiedAttributeUpdatedV1, bytes, manifest, currentVersion)
    case _                                                     =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
