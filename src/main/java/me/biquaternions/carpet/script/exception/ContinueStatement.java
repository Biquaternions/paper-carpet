package me.biquaternions.carpet.script.exception;

import me.biquaternions.carpet.script.value.Value;

import org.jspecify.annotations.Nullable;

public class ContinueStatement extends ExitStatement
{
    public ContinueStatement(@Nullable Value value)
    {
        super(value);
    }
}
