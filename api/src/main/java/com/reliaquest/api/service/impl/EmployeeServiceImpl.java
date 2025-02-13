package com.reliaquest.api.service.impl;

import com.reliaquest.api.constansts.ApiConstants;
import com.reliaquest.api.exception.ServiceException;
import com.reliaquest.api.model.CreateEmployeeRequest;
import com.reliaquest.api.model.DeleteEmployeeRequest;
import com.reliaquest.api.model.Employee;
import com.reliaquest.api.model.ServerEmployeeResponse;
import com.reliaquest.api.service.EmployeeService;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeServiceImpl.class);
    private final WebClient webClient;

    @Override
    public List<Employee> getAllEmployees() {
        try {
            ServerEmployeeResponse<List<Employee>> response = webClient.get()
                    .uri(ApiConstants.BASE_URL)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ServerEmployeeResponse<List<Employee>>>() {})
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .filter(this::isRateLimitError))
                    .block();

            return Optional.ofNullable(response)
                    .map(ServerEmployeeResponse::getData)
                    .orElse(Collections.emptyList());
        } catch (Exception ex) {
            logger.error("Error fetching employees: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<Employee> getEmployeesByNameSearch(String searchString) {
        return getAllEmployees().stream()
                .filter(e -> e.getName() != null && e.getName().equalsIgnoreCase(searchString))
                .collect(Collectors.toList());
    }

    @Override
    public Employee getEmployeeById(String id) {
        try {
            ServerEmployeeResponse<Employee> response = webClient.get()
                    .uri(ApiConstants.BASE_URL + "/" + id)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ServerEmployeeResponse<Employee>>() {})
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .filter(this::isRateLimitError))
                    .block();

            return Optional.ofNullable(response)
                    .map(ServerEmployeeResponse::getData)
                    .orElse(null);
        } catch (Exception ex) {
            logger.error("Error fetching employee by ID {}: {}", id, ex.getMessage());
            return null;
        }
    }

    @Override
    public Integer getHighestSalaryOfEmployees() {
        return getAllEmployees().stream()
                .map(Employee::getSalary)
                .max(Integer::compareTo)
                .orElse(0);
    }

    @Override
    public List<String> getTopTenHighestEarningEmployeeNames() {
        return getAllEmployees().stream()
                .sorted(Comparator.comparingInt(Employee::getSalary).reversed()) // Fix sorting: Descending order
                .limit(10)
                .map(Employee::getName)
                .collect(Collectors.toList());
    }

    @Override
    public Employee createEmployee(CreateEmployeeRequest employeeInput) {
        try {
            ServerEmployeeResponse<Employee> response = webClient.post()
                    .uri(ApiConstants.BASE_URL)
                    .body(BodyInserters.fromValue(employeeInput))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ServerEmployeeResponse<Employee>>() {})
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .filter(this::isRateLimitError))
                    .block();

            return Optional.ofNullable(response)
                    .map(ServerEmployeeResponse::getData)
                    .orElse(null);
        } catch (Exception ex) {
            logger.error("Error creating employee: {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public String deleteEmployeeById(String id) {
        try {
            Employee employee = getEmployeeById(id);
            if (employee == null || employee.getName() == null) {
                throw new IllegalArgumentException("Employee not found with ID: " + id);
            }

            DeleteEmployeeRequest deleteEmployeeRequest = new DeleteEmployeeRequest(employee.getName());

            ServerEmployeeResponse<Boolean> response = webClient.method(HttpMethod.DELETE)
                    .uri(ApiConstants.BASE_URL)
                    .bodyValue(deleteEmployeeRequest)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<ServerEmployeeResponse<Boolean>>() {})
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .filter(this::isRateLimitError)
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) ->
                                    new ServiceException("Failed to delete employee after multiple retries: " + id)))
                    .block();

            if (response != null && Boolean.TRUE.equals(response.getData())) {
                return "Successfully deleted employee with ID: " + id;
            } else {
                return "Failed to delete employee with ID: " + id;
            }
        } catch (Exception e) {
            logger.error("Unexpected error while deleting employee ID {}: {}", id, e.getMessage());
            return "Unexpected error: " + e.getMessage();
        }
    }


    private boolean isRateLimitError(Throwable throwable) {
        return throwable instanceof WebClientResponseException.TooManyRequests;
    }
}
