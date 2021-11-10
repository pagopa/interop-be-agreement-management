package it.pagopa.pdnd.interop.uservice.agreementmanagement.api

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{Directive1, Route}
import akka.http.scaladsl.marshalling.ToEntityMarshaller
    import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
    import akka.http.scaladsl.unmarshalling.FromStringUnmarshaller
import it.pagopa.pdnd.interop.uservice.agreementmanagement.server.AkkaHttpHelper._
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.Agreement
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.AgreementSeed
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.AgreementState
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.Problem
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.StateChangeDetails
import java.util.UUID
import it.pagopa.pdnd.interop.uservice.agreementmanagement.model.VerifiedAttributeSeed


    class AgreementApi(
    agreementService: AgreementApiService,
    agreementMarshaller: AgreementApiMarshaller,
    wrappingDirective: Directive1[Seq[(String, String)]]
    ) {
    
    import agreementMarshaller._

    lazy val route: Route =
        path("agreements" / Segment / "activate") { (agreementId) => 
        patch { wrappingDirective { implicit contexts =>  
            entity(as[StateChangeDetails]){ stateChangeDetails =>
              agreementService.activateAgreement(agreementId = agreementId, stateChangeDetails = stateChangeDetails)
            }
        }
        }
        } ~
        path("agreements") { 
        post { wrappingDirective { implicit contexts =>  
            entity(as[AgreementSeed]){ agreementSeed =>
              agreementService.addAgreement(agreementSeed = agreementSeed)
            }
        }
        }
        } ~
        path("agreement" / Segment) { (agreementId) => 
        get { wrappingDirective { implicit contexts =>  
            agreementService.getAgreement(agreementId = agreementId)
        }
        }
        } ~
        path("agreements") { 
        get { wrappingDirective { implicit contexts => 
            parameters("producerId".as[String].?, "consumerId".as[String].?, "eserviceId".as[String].?, "descriptorId".as[String].?, "state".as[String].?) { (producerId, consumerId, eserviceId, descriptorId, state) => 
            agreementService.getAgreements(producerId = producerId, consumerId = consumerId, eserviceId = eserviceId, descriptorId = descriptorId, state = state)
            }
        }
        }
        } ~
        path("agreements" / Segment / "suspend") { (agreementId) => 
        patch { wrappingDirective { implicit contexts =>  
            entity(as[StateChangeDetails]){ stateChangeDetails =>
              agreementService.suspendAgreement(agreementId = agreementId, stateChangeDetails = stateChangeDetails)
            }
        }
        }
        } ~
        path("agreements" / Segment / "attribute") { (agreementId) => 
        patch { wrappingDirective { implicit contexts =>  
            entity(as[VerifiedAttributeSeed]){ verifiedAttributeSeed =>
              agreementService.updateAgreementVerifiedAttribute(agreementId = agreementId, verifiedAttributeSeed = verifiedAttributeSeed)
            }
        }
        }
        } ~
        path("agreements" / Segment / "upgrade") { (agreementId) => 
        post { wrappingDirective { implicit contexts =>  
            entity(as[AgreementSeed]){ agreementSeed =>
              agreementService.upgradeAgreementById(agreementId = agreementId, agreementSeed = agreementSeed)
            }
        }
        }
        }
    }


    trait AgreementApiService {
          def activateAgreement200(responseAgreement: Agreement)(implicit toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]): Route =
            complete((200, responseAgreement))
  def activateAgreement400(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((400, responseProblem))
  def activateAgreement404(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((404, responseProblem))
        /**
           * Code: 200, Message: Agreement activated, DataType: Agreement
   * Code: 400, Message: Bad Request, DataType: Problem
   * Code: 404, Message: Agreement not found, DataType: Problem
        */
        def activateAgreement(agreementId: String, stateChangeDetails: StateChangeDetails)
            (implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], toEntityMarshallerAgreement: ToEntityMarshaller[Agreement], contexts: Seq[(String, String)]): Route

          def addAgreement200(responseAgreement: Agreement)(implicit toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]): Route =
            complete((200, responseAgreement))
  def addAgreement405(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((405, responseProblem))
        /**
           * Code: 200, Message: Agreement created, DataType: Agreement
   * Code: 405, Message: Invalid input, DataType: Problem
        */
        def addAgreement(agreementSeed: AgreementSeed)
            (implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], toEntityMarshallerAgreement: ToEntityMarshaller[Agreement], contexts: Seq[(String, String)]): Route

          def getAgreement200(responseAgreement: Agreement)(implicit toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]): Route =
            complete((200, responseAgreement))
  def getAgreement400(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((400, responseProblem))
  def getAgreement404(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((404, responseProblem))
        /**
           * Code: 200, Message: Agreement retrieved, DataType: Agreement
   * Code: 400, Message: Bad request, DataType: Problem
   * Code: 404, Message: Agreement not found, DataType: Problem
        */
        def getAgreement(agreementId: String)
            (implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], toEntityMarshallerAgreement: ToEntityMarshaller[Agreement], contexts: Seq[(String, String)]): Route

          def getAgreements200(responseAgreementarray: Seq[Agreement])(implicit toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]]): Route =
            complete((200, responseAgreementarray))
  def getAgreements400(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((400, responseProblem))
        /**
           * Code: 200, Message: A list of Agreement, DataType: Seq[Agreement]
   * Code: 400, Message: Bad Request, DataType: Problem
        */
        def getAgreements(producerId: Option[String], consumerId: Option[String], eserviceId: Option[String], descriptorId: Option[String], state: Option[String])
            (implicit toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]], toEntityMarshallerProblem: ToEntityMarshaller[Problem], contexts: Seq[(String, String)]): Route

          def suspendAgreement200(responseAgreement: Agreement)(implicit toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]): Route =
            complete((200, responseAgreement))
  def suspendAgreement400(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((400, responseProblem))
  def suspendAgreement404(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((404, responseProblem))
        /**
           * Code: 200, Message: OK, DataType: Agreement
   * Code: 400, Message: Bad Request, DataType: Problem
   * Code: 404, Message: Agreement Not Found, DataType: Problem
        */
        def suspendAgreement(agreementId: String, stateChangeDetails: StateChangeDetails)
            (implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], toEntityMarshallerAgreement: ToEntityMarshaller[Agreement], contexts: Seq[(String, String)]): Route

          def updateAgreementVerifiedAttribute200(responseAgreement: Agreement)(implicit toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]): Route =
            complete((200, responseAgreement))
  def updateAgreementVerifiedAttribute400(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((400, responseProblem))
  def updateAgreementVerifiedAttribute404(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((404, responseProblem))
        /**
           * Code: 200, Message: Returns the agreement with the updated attribute state., DataType: Agreement
   * Code: 400, Message: Bad Request, DataType: Problem
   * Code: 404, Message: Resource Not Found, DataType: Problem
        */
        def updateAgreementVerifiedAttribute(agreementId: String, verifiedAttributeSeed: VerifiedAttributeSeed)
            (implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], toEntityMarshallerAgreement: ToEntityMarshaller[Agreement], contexts: Seq[(String, String)]): Route

          def upgradeAgreementById200(responseAgreement: Agreement)(implicit toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]): Route =
            complete((200, responseAgreement))
  def upgradeAgreementById404(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((404, responseProblem))
  def upgradeAgreementById400(responseProblem: Problem)(implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem]): Route =
            complete((400, responseProblem))
        /**
           * Code: 200, Message: Agreement updated., DataType: Agreement
   * Code: 404, Message: Agreement not found, DataType: Problem
   * Code: 400, Message: Invalid ID supplied, DataType: Problem
        */
        def upgradeAgreementById(agreementId: String, agreementSeed: AgreementSeed)
            (implicit toEntityMarshallerProblem: ToEntityMarshaller[Problem], toEntityMarshallerAgreement: ToEntityMarshaller[Agreement], contexts: Seq[(String, String)]): Route

    }

        trait AgreementApiMarshaller {
          implicit def fromEntityUnmarshallerAgreementSeed: FromEntityUnmarshaller[AgreementSeed]

  implicit def fromEntityUnmarshallerStateChangeDetails: FromEntityUnmarshaller[StateChangeDetails]

  implicit def fromEntityUnmarshallerVerifiedAttributeSeed: FromEntityUnmarshaller[VerifiedAttributeSeed]


        
          implicit def toEntityMarshallerAgreementarray: ToEntityMarshaller[Seq[Agreement]]

  implicit def toEntityMarshallerProblem: ToEntityMarshaller[Problem]

  implicit def toEntityMarshallerAgreement: ToEntityMarshaller[Agreement]

        }

