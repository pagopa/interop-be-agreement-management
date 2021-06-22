package it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.serializer

import akka.serialization.SerializerWithStringManifest
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.AgreementAdded
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.persistence.serializer.v1._

import java.io.NotSerializableException

class AgreementAddedSerializer extends SerializerWithStringManifest {

  final val version1: String = "1"

  final val currentVersion: String = version1

  override def identifier: Int = 100000

  override def manifest(o: AnyRef): String = s"${o.getClass.getName}|$currentVersion"

  final val AgreementAddedManifest: String = classOf[AgreementAdded].getName

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case event: AgreementAdded =>
      serialize(event, AgreementAddedManifest, currentVersion)
  }

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  override def fromBinary(bytes: Array[Byte], manifest: String): AnyRef = manifest.split('|').toList match {
    case AgreementAddedManifest :: `version1` :: Nil =>
      deserialize(v1.events.AgreementAddedV1, bytes, manifest, currentVersion)
    case _ =>
      throw new NotSerializableException(
        s"Unable to handle manifest: [[$manifest]], currentVersion: [[$currentVersion]] "
      )
  }

}
