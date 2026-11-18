package server

import (
	"context"
	"fmt"
	"regexp"
	"strings"
	"time"

	"github.com/Pierorivera1/DinoGym/backend/internal/api"
	"github.com/Pierorivera1/DinoGym/backend/internal/store"
)

// DeviceStore keeps the phones that receive reminders (decision 106).
// *store.Store is the real one.
type DeviceStore interface {
	PutDevice(ctx context.Context, userID, installationID, token string, now time.Time) error
	DeleteDevice(ctx context.Context, userID, installationID string) error
}

var _ DeviceStore = (*store.Store)(nil)

// Reminders brings a user's reminder schedules in line with their stored
// settings (decision 105). *reminders.Reconciler is the real one.
type Reminders interface {
	Reconcile(ctx context.Context, userID string) error
}

// installationIDPattern is the contract's pattern for installationId. Path
// parameters are not checked by the body validator, so the handlers check it.
var installationIDPattern = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$`)

// reminderPrefix marks the preference keys that hold reminder settings.
const reminderPrefix = "reminder_"

// WithReminders sets what reconciles reminder schedules and returns s. Without
// it (the laptop, by default), settings sync but no schedule is made.
func (s *Server) WithReminders(r Reminders) *Server {
	s.reminders = r
	return s
}

// RegisterDevice stores the phone's FCM token and reconciles the user's
// schedules, which repairs any reconcile that failed after a push.
func (s *Server) RegisterDevice(ctx context.Context, req api.RegisterDeviceRequestObject) (api.RegisterDeviceResponseObject, error) {
	userID, err := userFrom(ctx)
	if err != nil {
		return nil, err
	}
	if !installationIDPattern.MatchString(req.InstallationId) {
		return api.RegisterDevice400JSONResponse{BadRequestJSONResponse: badRequest("installationId must be a UUID")}, nil
	}
	if req.Body == nil || req.Body.Token == "" {
		return api.RegisterDevice400JSONResponse{BadRequestJSONResponse: badRequest("token is required")}, nil
	}
	id := strings.ToLower(req.InstallationId)
	if err := s.store.PutDevice(ctx, userID, id, req.Body.Token, s.now()); err != nil {
		return nil, fmt.Errorf("register device: %w", err)
	}
	s.reconcile(ctx, userID)
	return api.RegisterDevice204Response{}, nil
}

// UnregisterDevice removes the phone, on sign-out.
func (s *Server) UnregisterDevice(ctx context.Context, req api.UnregisterDeviceRequestObject) (api.UnregisterDeviceResponseObject, error) {
	userID, err := userFrom(ctx)
	if err != nil {
		return nil, err
	}
	if !installationIDPattern.MatchString(req.InstallationId) {
		// Nothing can be stored under it, so there is nothing to delete.
		return api.UnregisterDevice204Response{}, nil
	}
	if err := s.store.DeleteDevice(ctx, userID, strings.ToLower(req.InstallationId)); err != nil {
		return nil, fmt.Errorf("unregister device: %w", err)
	}
	return api.UnregisterDevice204Response{}, nil
}

// reconcile runs the reconciler, if there is one. A failure is logged and
// does not fail the request: the next registration tries again.
func (s *Server) reconcile(ctx context.Context, userID string) {
	if s.reminders == nil {
		return
	}
	if err := s.reminders.Reconcile(ctx, userID); err != nil {
		s.log.ErrorContext(ctx, "reminder reconcile failed", "error", err)
	}
}

// touchesReminders reports whether an accepted row of a push is a reminder
// setting.
func touchesReminders(rows []store.Row, results []store.Result) bool {
	for i, r := range rows {
		if r.Kind == "PREF" && strings.HasPrefix(r.ID, reminderPrefix) && results[i].Status == store.Accepted {
			return true
		}
	}
	return false
}
