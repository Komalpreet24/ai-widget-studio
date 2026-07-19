package com.example.aiwidgetstudio.domain.model

data class WidgetTemplate(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val dslJson: String
)

object WidgetTemplateRepository {

    val templates: List<WidgetTemplate> = listOf(

        WidgetTemplate(
            id = "water_tracker",
            name = "Water Tracker",
            description = "Count glasses of water per day, resets at midnight",
            emoji = "💧",
            dslJson = """{"dslVersion":1,"metadata":{"name":"Water Tracker","size":"MEDIUM"},"data":{"updatePolicy":{"type":"DAILY_RESET","hour":0,"minute":0},"variables":[{"name":"glasses","type":"INT","default":0,"min":0,"max":12}]},"actions":[{"id":"drink","type":"INCREMENT","target":"glasses","step":1},{"id":"undo","type":"DECREMENT","target":"glasses","step":1},{"id":"reset","type":"RESET","target":"glasses"}],"ui":{"type":"COLUMN","style":{"backgroundColor":"#FFFFFF","padding":16},"children":[{"type":"TEXT","value":"💧 Water Tracker","style":{"textColor":"#1565C0"}},{"type":"TEXT","value":"{{glasses}} / 12 glasses","style":{"textColor":"#212121"}},{"type":"PROGRESS","current":"{{glasses}}","max":"12","style":{}},{"type":"ROW","style":{},"children":[{"type":"BUTTON","text":"+ Drink","action":"drink","style":{"backgroundColor":"#1E88E5","textColor":"#FFFFFF","cornerRadius":50}},{"type":"BUTTON","text":"Undo","action":"undo","style":{"backgroundColor":"#E3F2FD","textColor":"#1565C0","cornerRadius":50}}]}]}}"""
        ),

        WidgetTemplate(
            id = "habit_counter",
            name = "Habit Counter",
            description = "Track daily habit completions",
            emoji = "✅",
            dslJson = """{"dslVersion":1,"metadata":{"name":"Habit Counter","size":"MEDIUM"},"data":{"updatePolicy":{"type":"DAILY_RESET","hour":0,"minute":0},"variables":[{"name":"done","type":"BOOLEAN","default":false},{"name":"streak","type":"INT","default":0,"min":0,"max":365}]},"actions":[{"id":"complete","type":"SET_VALUE","target":"done","value":true},{"id":"uncomplete","type":"SET_VALUE","target":"done","value":false}],"ui":{"type":"COLUMN","style":{"backgroundColor":"#FFFFFF","padding":16},"children":[{"type":"TEXT","value":"✅ Daily Habit","style":{"textColor":"#212121"}},{"type":"TEXT","value":"Streak: {{streak}} days","style":{"textColor":"#388E3C"}},{"type":"BUTTON","text":"Mark Done","action":"complete","style":{"backgroundColor":"#43A047","textColor":"#FFFFFF","cornerRadius":50}}]}}"""
        ),

        WidgetTemplate(
            id = "focus_sessions",
            name = "Focus Sessions",
            description = "Count deep work sessions completed today",
            emoji = "🎯",
            dslJson = """{"dslVersion":1,"metadata":{"name":"Focus Sessions","size":"MEDIUM"},"data":{"updatePolicy":{"type":"DAILY_RESET","hour":0,"minute":0},"variables":[{"name":"sessions","type":"INT","default":0,"min":0,"max":20}]},"actions":[{"id":"done","type":"INCREMENT","target":"sessions","step":1},{"id":"reset","type":"RESET","target":"sessions"}],"ui":{"type":"COLUMN","style":{"backgroundColor":"#FFFFFF","padding":16},"children":[{"type":"TEXT","value":"🎯 Focus Sessions","style":{"textColor":"#212121"}},{"type":"TEXT","value":"{{sessions}} sessions today","style":{"textColor":"#5E35B1"}},{"type":"PROGRESS","current":"{{sessions}}","max":"8","style":{}},{"type":"ROW","style":{},"children":[{"type":"BUTTON","text":"+ Session","action":"done","style":{"backgroundColor":"#7E57C2","textColor":"#FFFFFF","cornerRadius":50}},{"type":"BUTTON","text":"Reset","action":"reset","style":{"backgroundColor":"#EDE7F6","textColor":"#5E35B1","cornerRadius":50}}]}]}}"""
        ),

        WidgetTemplate(
            id = "mood_tracker",
            name = "Mood Tracker",
            description = "Log how you feel today with one tap",
            emoji = "😊",
            dslJson = """{"dslVersion":1,"metadata":{"name":"Mood Tracker","size":"MEDIUM"},"data":{"updatePolicy":{"type":"NONE"},"variables":[{"name":"mood","type":"STRING","default":"—"}]},"actions":[{"id":"great","type":"SET_VALUE","target":"mood","value":"😄 Great"},{"id":"good","type":"SET_VALUE","target":"mood","value":"🙂 Good"},{"id":"okay","type":"SET_VALUE","target":"mood","value":"😐 Okay"},{"id":"bad","type":"SET_VALUE","target":"mood","value":"😔 Not great"}],"ui":{"type":"COLUMN","style":{"backgroundColor":"#FFFFFF","padding":16},"children":[{"type":"TEXT","value":"Today's mood","style":{"textColor":"#757575"}},{"type":"TEXT","value":"{{mood}}","style":{"textColor":"#212121"}},{"type":"ROW","style":{},"children":[{"type":"BUTTON","text":"😄","action":"great","style":{"backgroundColor":"#FFF9C4","textColor":"#F57F17","cornerRadius":50}},{"type":"BUTTON","text":"🙂","action":"good","style":{"backgroundColor":"#E8F5E9","textColor":"#2E7D32","cornerRadius":50}},{"type":"BUTTON","text":"😐","action":"okay","style":{"backgroundColor":"#F5F5F5","textColor":"#616161","cornerRadius":50}},{"type":"BUTTON","text":"😔","action":"bad","style":{"backgroundColor":"#EDE7F6","textColor":"#4527A0","cornerRadius":50}}]}]}}"""
        ),

        WidgetTemplate(
            id = "step_counter",
            name = "Step Counter",
            description = "Manually log steps toward your daily goal",
            emoji = "👟",
            dslJson = """{"dslVersion":1,"metadata":{"name":"Step Counter","size":"MEDIUM"},"data":{"updatePolicy":{"type":"DAILY_RESET","hour":0,"minute":0},"variables":[{"name":"steps","type":"INT","default":0,"min":0,"max":30000}]},"actions":[{"id":"add500","type":"INCREMENT","target":"steps","step":500},{"id":"add1000","type":"INCREMENT","target":"steps","step":1000},{"id":"reset","type":"RESET","target":"steps"}],"ui":{"type":"COLUMN","style":{"backgroundColor":"#FFFFFF","padding":16},"children":[{"type":"TEXT","value":"👟 Steps","style":{"textColor":"#212121"}},{"type":"TEXT","value":"{{steps}} / 10000","style":{"textColor":"#E65100"}},{"type":"PROGRESS","current":"{{steps}}","max":"10000","style":{}},{"type":"ROW","style":{},"children":[{"type":"BUTTON","text":"+500","action":"add500","style":{"backgroundColor":"#FF7043","textColor":"#FFFFFF","cornerRadius":50}},{"type":"BUTTON","text":"+1000","action":"add1000","style":{"backgroundColor":"#BF360C","textColor":"#FFFFFF","cornerRadius":50}}]}]}}"""
        ),

        WidgetTemplate(
            id = "budget_tracker",
            name = "Budget Tracker",
            description = "Track daily spending against your budget",
            emoji = "💰",
            dslJson = """{"dslVersion":1,"metadata":{"name":"Budget Tracker","size":"MEDIUM"},"data":{"updatePolicy":{"type":"DAILY_RESET","hour":0,"minute":0},"variables":[{"name":"spent","type":"INT","default":0,"min":0,"max":10000}]},"actions":[{"id":"add5","type":"INCREMENT","target":"spent","step":5},{"id":"add10","type":"INCREMENT","target":"spent","step":10},{"id":"add20","type":"INCREMENT","target":"spent","step":20},{"id":"reset","type":"RESET","target":"spent"}],"ui":{"type":"COLUMN","style":{"backgroundColor":"#FFFFFF","padding":16},"children":[{"type":"TEXT","value":"💰 Daily Budget","style":{"textColor":"#212121"}},{"type":"TEXT","value":"Spent: {{spent}} of 100","style":{"textColor":"#C62828"}},{"type":"PROGRESS","current":"{{spent}}","max":"100","style":{}},{"type":"ROW","style":{},"children":[{"type":"BUTTON","text":"+5","action":"add5","style":{"backgroundColor":"#EF9A9A","textColor":"#B71C1C","cornerRadius":50}},{"type":"BUTTON","text":"+10","action":"add10","style":{"backgroundColor":"#EF5350","textColor":"#FFFFFF","cornerRadius":50}},{"type":"BUTTON","text":"+20","action":"add20","style":{"backgroundColor":"#C62828","textColor":"#FFFFFF","cornerRadius":50}}]}]}}"""
        ),

        WidgetTemplate(
            id = "sleep_tracker",
            name = "Sleep Tracker",
            description = "Log hours of sleep each night",
            emoji = "😴",
            dslJson = """{"dslVersion":1,"metadata":{"name":"Sleep Tracker","size":"MEDIUM"},"data":{"updatePolicy":{"type":"NONE"},"variables":[{"name":"hours","type":"INT","default":0,"min":0,"max":12}]},"actions":[{"id":"add","type":"INCREMENT","target":"hours","step":1},{"id":"remove","type":"DECREMENT","target":"hours","step":1},{"id":"reset","type":"RESET","target":"hours"}],"ui":{"type":"COLUMN","style":{"backgroundColor":"#FFFFFF","padding":16},"children":[{"type":"TEXT","value":"😴 Last Night","style":{"textColor":"#212121"}},{"type":"TEXT","value":"{{hours}} hours","style":{"textColor":"#283593"}},{"type":"PROGRESS","current":"{{hours}}","max":"9","style":{}},{"type":"ROW","style":{},"children":[{"type":"BUTTON","text":"−","action":"remove","style":{"backgroundColor":"#E8EAF6","textColor":"#283593","cornerRadius":50}},{"type":"BUTTON","text":"+","action":"add","style":{"backgroundColor":"#3949AB","textColor":"#FFFFFF","cornerRadius":50}}]}]}}"""
        ),

        WidgetTemplate(
            id = "gratitude_log",
            name = "Gratitude Log",
            description = "Count gratitude entries logged today",
            emoji = "🙏",
            dslJson = """{"dslVersion":1,"metadata":{"name":"Gratitude Log","size":"SMALL"},"data":{"updatePolicy":{"type":"DAILY_RESET","hour":0,"minute":0},"variables":[{"name":"entries","type":"INT","default":0,"min":0,"max":10}]},"actions":[{"id":"log","type":"INCREMENT","target":"entries","step":1}],"ui":{"type":"COLUMN","style":{"backgroundColor":"#FFFFFF","padding":16},"children":[{"type":"TEXT","value":"🙏 Gratitude","style":{"textColor":"#212121"}},{"type":"TEXT","value":"{{entries}} today","style":{"textColor":"#AD1457"}},{"type":"BUTTON","text":"+ Add","action":"log","style":{"backgroundColor":"#F48FB1","textColor":"#880E4F","cornerRadius":50}}]}}"""
        )
    )
}
