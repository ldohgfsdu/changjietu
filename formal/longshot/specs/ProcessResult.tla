------------------------------ MODULE ProcessResult ------------------------------
EXTENDS Naturals

CONSTANT MaxEvents

VARIABLES sequence, pending, delivered, phase, manualEdits

vars == <<sequence, pending, delivered, phase, manualEdits>>

Init ==
    /\ sequence = 0
    /\ pending = 0
    /\ delivered = 0
    /\ phase = "ready"
    /\ manualEdits = 0

Begin ==
    /\ phase \in {"ready", "done", "failed"}
    /\ sequence < MaxEvents
    /\ phase' = "processing"
    /\ UNCHANGED <<sequence, pending, delivered, manualEdits>>

Complete ==
    /\ phase = "processing"
    /\ sequence' = sequence + 1
    /\ pending' = sequence + 1
    /\ phase' = "done"
    /\ UNCHANGED <<delivered, manualEdits>>

Fail ==
    /\ phase = "processing"
    /\ phase' = "failed"
    /\ UNCHANGED <<sequence, pending, delivered, manualEdits>>

Ack ==
    /\ pending # 0
    /\ pending' = 0
    /\ delivered' = delivered + 1
    /\ UNCHANGED <<sequence, phase, manualEdits>>

ManualEdit ==
    /\ phase = "done"
    /\ manualEdits < MaxEvents
    /\ manualEdits' = manualEdits + 1
    /\ UNCHANGED <<sequence, pending, delivered, phase>>

Stop ==
    /\ sequence = MaxEvents
    /\ phase # "processing"
    /\ pending = 0
    /\ UNCHANGED vars

Next == Begin \/ Complete \/ Fail \/ Ack \/ ManualEdit \/ Stop

Spec == Init /\ [][Next]_vars

TypeOK ==
    /\ sequence \in 0..MaxEvents
    /\ pending \in 0..MaxEvents
    /\ delivered \in 0..MaxEvents
    /\ manualEdits \in 0..MaxEvents
    /\ phase \in {"ready", "processing", "done", "failed"}

NoPhantomNavigation == delivered <= sequence

PendingOnlyAfterSuccess == pending = 0 \/ pending <= sequence
=============================================================================