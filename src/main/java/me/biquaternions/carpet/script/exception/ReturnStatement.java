package me.biquaternions.carpet.script.exception;

import me.biquaternions.carpet.script.value.Value;

public class ReturnStatement extends ExitStatement
{
    public ReturnStatement(Value value)
    {
        super(value);
    }
}
