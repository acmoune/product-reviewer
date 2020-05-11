package models

import org.apache.avro.specific.SpecificRecord

trait DomainModel {
  def toDataModel: SpecificRecord
}
