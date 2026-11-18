// Package reminders keeps each user's EventBridge schedules in line with
// their reminder settings, and sends the reminders when a schedule fires
// (task 24, decisions 104–107).
//
// The server only decides when and to whom. A reminder is an FCM data
// message holding just its kind; the app writes the notification from its
// own data and string resources (decision 104).
package reminders

import (
	"fmt"
	"regexp"
	"time"
	_ "time/tzdata" // Lambda's provided.al2023 image has no zoneinfo; embed it.
)

// Kind is what a reminder is about. Its value is the data message's "kind".
type Kind string

const (
	WorkoutDay    Kind = "workout_day"
	WeeklySummary Kind = "weekly_summary"
)

// Kinds lists every kind, in a fixed order.
var Kinds = []Kind{WorkoutDay, WeeklySummary}

// The synced preference keys the app writes (decision 105).
const (
	KeyWorkoutDay = "reminder_workout_day"
	KeyWeekly     = "reminder_weekly"
	KeyTime       = "reminder_time"
	KeyTimeZone   = "reminder_time_zone"
)

// DefaultTime is the workout-day time when none is stored.
const DefaultTime = "08:00"

// The weekly summary goes out on Sundays at 20:00, the user's time.
const weeklyCron = "cron(0 20 ? * SUN *)"

var timePattern = regexp.MustCompile(`^([01][0-9]|2[0-3]):([0-5][0-9])$`)

// Settings are one user's reminder settings.
type Settings struct {
	WorkoutDay bool
	Weekly     bool
	// Time is the workout-day time, "HH:mm", 24-hour.
	Time string
	// TimeZone is an IANA name such as "America/Lima".
	TimeZone string
}

// ParseSettings reads the stored preference rows. A switch that is missing
// or not "true" is off. A bad time or zone turns every reminder off and
// returns the reason, so a phone that writes garbage gets no schedules rather
// than schedules at a wrong hour.
func ParseSettings(prefs map[string]string) (Settings, error) {
	s := Settings{
		WorkoutDay: prefs[KeyWorkoutDay] == "true",
		Weekly:     prefs[KeyWeekly] == "true",
		Time:       prefs[KeyTime],
		TimeZone:   prefs[KeyTimeZone],
	}
	if s.Time == "" {
		s.Time = DefaultTime
	}
	if !s.WorkoutDay && !s.Weekly {
		return s, nil
	}
	if !timePattern.MatchString(s.Time) {
		return Settings{}, fmt.Errorf("%s %q is not HH:mm", KeyTime, s.Time)
	}
	if s.TimeZone == "" {
		return Settings{}, fmt.Errorf("%s is not set", KeyTimeZone)
	}
	if _, err := time.LoadLocation(s.TimeZone); err != nil {
		return Settings{}, fmt.Errorf("%s %q: %w", KeyTimeZone, s.TimeZone, err)
	}
	return s, nil
}

// On reports whether the reminder of kind k is turned on.
func (s Settings) On(k Kind) bool {
	switch k {
	case WorkoutDay:
		return s.WorkoutDay
	case WeeklySummary:
		return s.Weekly
	}
	return false
}

// cron returns the schedule expression for kind k. Scheduler applies the
// time zone, daylight saving included (decision 105).
func (s Settings) cron(k Kind) string {
	if k == WeeklySummary {
		return weeklyCron
	}
	m := timePattern.FindStringSubmatch(s.Time)
	return fmt.Sprintf("cron(%s %s * * ? *)", trimZero(m[2]), trimZero(m[1]))
}

func trimZero(s string) string {
	if len(s) == 2 && s[0] == '0' {
		return s[1:]
	}
	return s
}

// scheduleName is the schedule's name in the environment's group: the user
// id (a 36-character UUID) and a short suffix, well under Scheduler's 64.
func scheduleName(userID string, k Kind) string {
	if k == WeeklySummary {
		return userID + "-week"
	}
	return userID + "-day"
}
