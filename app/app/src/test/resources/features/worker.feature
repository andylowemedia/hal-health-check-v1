@worker-code
Feature: Health Check Worker
  Scenario: Execute health check worker
    Given message has been sent to queue
    When the health check has been performed
    Then a message was sent to exhcnage and queue with results