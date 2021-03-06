/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an
 * "AS IS" BASIS,  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.powertac.genericcustomerservice

import java.util.List

import org.joda.time.Instant
import org.powertac.common.CustomerInfo
import org.powertac.common.PluginConfig
import org.powertac.common.enumerations.CustomerType
import org.powertac.common.enumerations.PowerType
import org.powertac.common.interfaces.NewTariffListener
import org.powertac.common.interfaces.TimeslotPhaseProcessor
import org.powertac.genericcustomer.GenericProducer


class GenericProducerService implements TimeslotPhaseProcessor {
  static transactional = false

  def timeService // autowire
  def competitionControlService
  def tariffMarketService

  // JEC - this attribute is a property of a CustomerInfo. Why is it here?
  int population = 1

  void init(PluginConfig config)
  {
    competitionControlService?.registerTimeslotPhase(this, 1)

    //Implemented in each customer model not here.
    def listener = [publishNewTariffs:{tariffList -> GenericProducer.list().each{ it.possibilityEvaluationNewTariffs(tariffList)}}] as NewTariffListener
    tariffMarketService?.registerNewTariffListener(listener)

    Integer value = config.configuration['population']?.toInteger()
    if (value == null) {
      log.error "Missing value for population. Default is ${population}"
    }
    population = value

    Integer numberOfProducers = config.configuration['numberOfProducers']?.toInteger()
    if (value == null) {
      log.error "Missing value for numberOfProducers. Default is 0"
      numberOfProducers = 0
    }
    for (int i = 1; i < numberOfProducers + 1; i++){
      def genericProducerInfo =
          new CustomerInfo(Name: "GenericProducer " + i,customerType: CustomerType.CustomerProducer,
          population: population, powerTypes: [PowerType.PRODUCTION])
      assert(genericProducerInfo.save())
      def genericProducer = new GenericProducer(customerInfo: genericProducerInfo)
      genericProducer.init()
      genericProducer.subscribeDefault()
      assert(genericProducer.save())
    }
  }

  public List<CustomerInfo> generateProducerInfoList()
  {
    List<CustomerInfo> result = []

    GenericProducer.list().each { producer ->
      result.add(producer.customerInfo)
    }
    println(result)
    return result
  }

  void activate(Instant now, int phase)
  {
    log.info "Activate"
    def genericProducerList = GenericProducer.list()
    if (genericProducerList.size() > 0) {
      if (phase == 1) {
        log.info "Phase 1"
        genericProducerList*.step()
      }
      else {
        // should never get here
        log.info "Phase 2"
        genericProducerList*.toString()
      }
    }
  }
}