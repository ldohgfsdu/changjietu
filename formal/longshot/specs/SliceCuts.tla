------------------------------ MODULE SliceCuts ------------------------------
EXTENDS Naturals

CONSTANT Height, MaxEncodes

VARIABLES desiredCut, resultEnd, resultStart, phase, encodes

vars == <<desiredCut, resultEnd, resultStart, phase, encodes>>

Init ==
    /\ desiredCut = 2
    /\ resultEnd = 2
    /\ resultStart = 2
    /\ phase = "ready"
    /\ encodes = 0

Drag(k) ==
    /\ phase = "ready"
    /\ k \in 1..(Height - 1)
    /\ desiredCut' = k
    /\ phase' = "editing"
    /\ UNCHANGED <<resultEnd, resultStart, encodes>>

CommitChanged ==
    /\ phase = "editing"
    /\ desiredCut # resultEnd
    /\ encodes < MaxEncodes
    /\ phase' = "encoding"
    /\ UNCHANGED <<desiredCut, resultEnd, resultStart, encodes>>

CommitNoop ==
    /\ phase = "editing"
    /\ desiredCut = resultEnd
    /\ phase' = "ready"
    /\ UNCHANGED <<desiredCut, resultEnd, resultStart, encodes>>

FinishEncode ==
    /\ phase = "encoding"
    /\ resultEnd' = desiredCut
    /\ resultStart' = desiredCut
    /\ encodes' = encodes + 1
    /\ phase' = "ready"
    /\ UNCHANGED desiredCut

Stop ==
    /\ encodes = MaxEncodes
    /\ UNCHANGED vars

Next ==
    \/ \E k \in 1..(Height - 1) : Drag(k)
    \/ CommitChanged
    \/ CommitNoop
    \/ FinishEncode
    \/ Stop

Spec == Init /\ [][Next]_vars

TypeOK ==
    /\ desiredCut \in 1..(Height - 1)
    /\ resultEnd \in 1..(Height - 1)
    /\ resultStart \in 1..(Height - 1)
    /\ encodes \in 0..MaxEncodes
    /\ phase \in {"ready", "editing", "encoding"}

NoGapNoOverlap == resultEnd = resultStart

ExactTotalHeight == resultEnd + (Height - resultStart) = Height
=============================================================================