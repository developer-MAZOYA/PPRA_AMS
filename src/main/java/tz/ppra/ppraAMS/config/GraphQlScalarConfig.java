package tz.ppra.ppraAMS.config;

import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.StringValue;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

import java.util.Locale;

@Configuration
public class GraphQlScalarConfig {

    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        return wiringBuilder -> wiringBuilder.scalar(jsonScalar());
    }

    private static GraphQLScalarType jsonScalar() {
        return GraphQLScalarType.newScalar()
                .name("JSON")
                .description("Arbitrary JSON value")
                .coercing(new Coercing<Object, Object>() {

                    @Override
                    public Object serialize(Object dataFetcherResult,
                                            GraphQLContext graphQLContext,
                                            Locale locale) throws CoercingSerializeException {
                        return dataFetcherResult;
                    }

                    @Override
                    public Object parseValue(Object input,
                                             GraphQLContext graphQLContext,
                                             Locale locale) throws CoercingParseValueException {
                        return input;
                    }

                    @Override
                    public Object parseLiteral(Value<?> input,
                                               CoercedVariables variables,
                                               GraphQLContext graphQLContext,
                                               Locale locale) throws CoercingParseLiteralException {
                        if (input instanceof StringValue sv) return sv.getValue();
                        return null;
                    }
                })
                .build();
    }
}
