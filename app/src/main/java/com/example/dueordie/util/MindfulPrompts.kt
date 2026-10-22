package com.example.dueordie.util

import kotlin.random.Random

/**
 * Curated repository of 100 mindful pause prompts.
 * Designed to provide gentle friction, grounding awareness, witty perspective,
 * and cognitive clarity whenever an impulsive or distracting app is opened.
 */
object MindfulPrompts {

    val PROMPTS: List<String> = listOf(
        // 1 - 10: Impulsive Check & Awareness
        "Is this intentional, or is your thumb on autopilot?",
        "Take two slow breaths. Decide if you really want to be here.",
        "Notice the urge to scroll. Let it peak and pass like a wave.",
        "What would feel useful to do here right now?",
        "Are you actually tired, bored, or avoiding something difficult?",
        "Your attention is your life in real-time. Where do you want it?",
        "Pause. Feel the air entering your lungs before you tap.",
        "You don't have to fill every quiet second with a screen.",
        "One deep inhale. Check in with your body before continuing.",
        "Is this app serving you right now, or are you serving it?",

        // 11 - 20: Grounding & Physical Reality
        "Feel your feet on the ground. Unclench your jaw.",
        "Drop your shoulders away from your ears. Take a deep breath.",
        "Look up from the screen for three seconds. Notice the room around you.",
        "Breathe in slow through your nose. Exhale the rush.",
        "Take a slow breath and decide what would feel helpful next.",
        "Relax your forehead and eyes. Take one deliberate breath.",
        "Check your posture. Straighten your spine and breathe easy.",
        "Inhale stillness, exhale urgency. There is no emergency here.",
        "Notice the weight of the phone in your hand. Breathe gently.",
        "A slow breath gives you a moment to choose what comes next.",

        // 21 - 30: Perspective & Reality Check
        "Social media will still be chaotic in ten minutes.",
        "What would you like to get from this visit?",
        "What would you like to give your attention to today?",
        "Comparison is the thief of peace. Protect your calm.",
        "The world won't end if you put the phone down right now.",
        "Would a short visit here feel good to you right now?",
        "Every notification is just someone asking for your precious time.",
        "You are not missing out on anything truly essential.",
        "What will you remember about today ten years from now?",
        "The endless scroll never ends, but your day will.",

        // 31 - 40: Focus & Intentionality
        "The task ahead is lighter than the weight of delaying it.",
        "Finish what matters first. The reward feels ten times better.",
        "Two minutes of focus beats two hours of scattered distraction.",
        "What was the one thing you promised yourself you'd finish today?",
        "Momentum is fragile. Guard it fiercely.",
        "A quick glance usually becomes half an hour. Be honest with yourself.",
        "Choose clarity over comfort. One breath at a time.",
        "Do what future you will thank you for.",
        "Small disciplines create immense freedom.",
        "Take one slow breath. Step back into what matters to you.",

        // 41 - 50: Calm & Decompression
        "Breathe in for four counts. Hold for two. Release for four.",
        "You don't need more information; you need a moment of quiet.",
        "Let the rush dissolve. You are exactly where you need to be.",
        "Give your brain ten seconds of zero input.",
        "Silence is not empty; it is full of recovery.",
        "Slow down. Rushing through your day makes everything blur.",
        "Soft belly, deep inhale, complete exhale.",
        "Rest doesn't always look like scrolling.",
        "Give yourself permission to just be still for a moment.",
        "Breathe deeply. Peace is found right between your thoughts.",

        // 51 - 60: Grounding & Quiet Clarity
        "Pause for a moment. Did you choose to open this app?",
        "Notice what drew you here. Take a breath before opening.",
        "Slow your breathing. Let your attention settle.",
        "Notice the physical room around you. Take a steady breath.",
        "Check in with your body and mind before continuing.",
        "A quiet moment of stillness before deciding.",
        "Close the app if you are ready for a change of pace.",
        "One deliberate breath before you continue.",
        "Breathe. Don't let an icon dictate your attention span.",
        "Take a gentle stretch. Inhale and exhale slowly.",

        // 61 - 70: Mindful Choices & Self-Compassion
        "Be kind to yourself. Acknowledge the urge and choose wisely.",
        "You don't have to be perfect today, just present.",
        "Give yourself the grace of a slow, uninterrupted moment.",
        "Honor your energy. It's okay to feel drained, just breathe.",
        "Notice how you feel. Take one easy breath before you continue.",
        "You are worthy of uninterrupted peace of mind.",
        "Treat your attention like the rare treasure it is.",
        "Notice without judgment. Just two deep breaths.",
        "Self-control is an act of deep self-respect.",
        "You deserve a life lived in high definition, not on a screen.",

        // 71 - 80: Boundaries & Space
        "Step away from the screen for a moment of quiet.",
        "You choose where your attention goes today.",
        "Reclaim the next ten minutes for your own peace.",
        "Unplugging for a moment gives your mind room to spark creativity.",
        "Look up from your device. Take in the space around you.",
        "Each deliberate pause builds a calmer habit.",
        "Choose real air, real light, and real conversations.",
        "You can set your phone down whenever you feel ready.",
        "Give your attention to what truly serves you.",
        "You can choose when this visit is enough.",

        // 81 - 90: Deep Breathing & Reset
        "Inhale deeply through your nose. Feel your chest expand.",
        "Exhale slowly and let all residual tension drop away.",
        "One full, deliberate breath cycle. Reset your momentum.",
        "Deep breath in: fill your lungs. Slow breath out: let it go.",
        "Breathe into this exact second. This second is all there is.",
        "As you inhale, feel grounded. As you exhale, feel clear.",
        "Fill your lungs to capacity. Hold for a moment. Release gently.",
        "Try one slow breath, then continue when you are ready.",
        "Let your breath anchor you to the physical room.",
        "Two mindful breaths can change the trajectory of your whole evening.",

        // 91 - 100: Purpose & Evening Reflections
        "What matters most to you today? Focus on that.",
        "Live your story, don't just watch other people live theirs.",
        "Make space for real thoughts to surface.",
        "When you look back on this day, will this tap matter?",
        "Recharge your spirit, not just your phone battery.",
        "Your potential is waiting for you in the real world.",
        "Step back into your day with fresh intention.",
        "Be present for the people who are in front of you.",
        "Clear mind, steady heart, slow breath.",
        "You are ready. Choose with purpose."
    )

    /**
     * Retrieves a random mindful prompt from the curated repository of 100 prompts.
     */
    fun getRandomPrompt(): String {
        return PROMPTS[Random.nextInt(PROMPTS.size)]
    }

    /**
     * Deterministically rotates prompts based on package name and attempt count.
     */
    fun getPromptForPackage(packageName: String, attemptCount: Int): String {
        val index = (Math.abs(packageName.hashCode()) + attemptCount) % PROMPTS.size
        return PROMPTS[index]
    }
}
