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
import org.powertac.genericcustomer.GenericConsumer


class GenericConsumerService implements TimeslotPhaseProcessor {
  static transactional = false

  def timeService // autowire
  def competitionControlService
  def tariffMarketService

  // JEC - this attribute is a property of a CustomerInfo. Why is it here?
  int population = 0

  void init(PluginConfig config)
  {
    competitionControlService?.registerTimeslotPhase(this, 1)

    //Implemented in each consumer model not here.
    def listener = [publishNewTariffs:{tariffList -> GenericConsumer.list().each{ it.possibilityEvaluationNewTariffs(tariffList)}}] as NewTariffListener
    tariffMarketService?.registerNewTariffListener(listener)

    Integer value = config.configuration['population']?.toInteger()
    if (value == null) {
      log.error "Missing value for population. Default is ${population}"
    }
    population = value

    Integer numberOfConsumers = config.configuration['numberOfConsumers']?.toInteger()
    if (value == null) {
      log.error "Missing value for numberOfConsumers. Default is 0"
      numberOfConsumers = 0
    }
    for (int i = 1; i < numberOfConsumers + 1; i++){
      def genericConsumerInfo =
          new CustomerInfo(Name: "GenericConsumer " + i,customerType: CustomerType.CustomerHousehold,
          population: population, powerTypes: [PowerType.CONSUMPTION])
      assert(genericConsumerInfo.save())
      def genericConsumer = new GenericConsumer(customerInfo: genericConsumerInfo)
      genericConsumer.init()
      genericConsumer.subscribeDefault()
      assert(genericConsumer.save())
    }
  }

  public List<CustomerInfo> generateCustomerInfoList()
  {
    List<CustomerInfo> result = []

    GenericConsumer.list().each { consumer ->
      result.add(consumer.customerInfo)
    }
    return result
  }

  public List<Map> generateBootstrapData()
  {
    List<Map> result = []

    GenericConsumer.list().each { consumer ->
      Map bootstrap = [:]
      bootstrap[consumer.customerInfo.name] = consumer.getBootstrapData()
      result.add(bootstrap)
    }
    return result
  }

  void activate(Instant now, int phase)
  {
    log.info "Activate"
    def genericConsumerList = GenericConsumer.list()
    if (genericConsumerList.size() > 0) {
      if (phase == 1) {
        log.info "Phase 1"
        genericConsumerList*.step()
      }
      else {
        // should never get here
        log.info "Phase 2"
        genericConsumerList*.toString()
      }
    }
  }
}