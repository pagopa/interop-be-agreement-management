package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.AgreementActivated
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class AgreementActivatedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 100002

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val AgreementActivatedManifest: String = classOf[AgreementActivated].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: AgreementActivated =>
      serialize(event, AgreementActivatedManifest, currentVersion)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case AgreementActivatedManifest :: `version1` :: Nil =>
      deserialize(v1.events.AgreementActivatedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
