package me.biquaternions.carpet.script.exception;

import java.util.List;

import me.biquaternions.carpet.script.Context;
import me.biquaternions.carpet.script.Expression;
import me.biquaternions.carpet.script.Token;
import me.biquaternions.carpet.script.value.FunctionValue;
import me.biquaternions.carpet.script.value.Value;

public class ProcessedThrowStatement extends ExpressionException
{
    public final Throwables thrownExceptionType;
    public final Value data;

    public ProcessedThrowStatement(Context c, Expression e, Token token, List<FunctionValue> stack, Throwables thrownExceptionType, Value data)
    {
        super(c, e, token, () -> "Unhandled " + thrownExceptionType.getId() + " exception: " + data.getString(), stack);
        this.thrownExceptionType = thrownExceptionType;
        this.data = data;
    }
}
