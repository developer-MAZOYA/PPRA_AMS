package tz.ppra.ppraAMS.exception;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GraphQlExceptionHandler extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            ErrorType type = switch (status) {
                case NOT_FOUND -> ErrorType.NOT_FOUND;
                case BAD_REQUEST, CONFLICT, UNPROCESSABLE_ENTITY -> ErrorType.BAD_REQUEST;
                case UNAUTHORIZED, FORBIDDEN -> ErrorType.UNAUTHORIZED;
                default -> ErrorType.INTERNAL_ERROR;
            };

            return GraphqlErrorBuilder.newError(env)
                    .message(rse.getReason() != null ? rse.getReason() : rse.getMessage())
                    .errorType(type)
                    .build();
        }

        if (ex instanceof IllegalArgumentException) {
            return GraphqlErrorBuilder.newError(env)
                    .message(ex.getMessage())
                    .errorType(ErrorType.BAD_REQUEST)
                    .build();
        }

        return null;
    }
}
