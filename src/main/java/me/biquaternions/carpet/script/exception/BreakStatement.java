package me.biquaternions.carpet.script.exception;

import me.biquaternions.carpet.script.value.Value;

import org.jspecify.annotations.Nullable;

public class BreakStatement extends ExitStatement
{
    public BreakStatement(@Nullable Value value)
    {
        super(value);
    }
}
